# -*- coding: utf-8 -*-
"""
MAG-FI | Phase 1 - Python Bridge & SQLite Generation Pipeline
Author : Team Antigravity (Data Engineer)
Input  : raw_mapping_1_3_<timestamp>.csv  (Floor 3 dataset)
Output : map_database.db  (production-ready fingerprint store)

Pipeline steps
--------------
  1. Data Ingestion     - Load CSV, skip metadata comment header (#)
  2. Magnetic Smoothing - Centered rolling mean (window = 5) on mag_x/y/z
  3. Wi-Fi Normalization- Fix semicolon->comma delimiter bug from App 1
                         + deduplicate repeated SSID keys (keep last seen)
  4. DB Compilation     - Write cleaned rows into SQLite fingerprints table
"""

import io
import os
import re
import sys
import json
import sqlite3

import pandas as pd

# Force UTF-8 output on Windows so progress messages render cleanly
if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")


# ─── Configuration ────────────────────────────────────────────────────────────

MAG_SMOOTH_WINDOW = 5          # rolling-average window size for magnetometer
DB_TABLE          = "fingerprints"


# ─── Step 3 helper: Wi-Fi JSON normaliser ─────────────────────────────────────

def fix_wifi_json(payload: str) -> str:
    """
    Fix the App-1 export bug where JSON object fields are separated by
    semicolons instead of commas, e.g.:

        {"VIT_TPO": -60; "VIT_VIP": -61}   ->   {"VIT_TPO": -60, "VIT_VIP": -61}

    Additionally, App 1 sometimes emits duplicate SSID keys inside the same
    scan object.  Python's json.loads() silently keeps the *last* value for
    each duplicate key, which is the correct LRU-style behaviour -- we retain
    that semantic here.

    The raw CSV has an additional quoting issue: pandas strips the outer CSV
    quote char but leaves the inner JSON string partially malformed (e.g.,
    {VIT_TPO": ...).  We use regex-based key-value extraction as a fallback
    to robustly reconstruct valid JSON from any partially-broken payload.

    Returns a compact, valid JSON string, or "{}" on any unrecoverable error.
    """
    if not isinstance(payload, str) or not payload.strip():
        return "{}"

    # --- Attempt 1: straightforward semicolon swap + json.loads ---------------
    try:
        cleaned = payload.replace(";", ",")
        # Ensure it is wrapped in braces (in case outer brace was stripped)
        stripped = cleaned.strip()
        if not stripped.startswith("{"):
            stripped = "{" + stripped
        if not stripped.endswith("}"):
            stripped = stripped + "}"
        parsed = json.loads(stripped)
        return json.dumps(parsed, separators=(",", ":"))
    except (json.JSONDecodeError, TypeError, ValueError):
        pass

    # --- Attempt 2: regex-based key-value extraction --------------------------
    # Pattern: match   "SSID_NAME": -integer   pairs wherever they appear
    kv_pattern = re.compile(r'"([^"]+)"\s*:\s*(-?\d+(?:\.\d+)?)')
    matches = kv_pattern.findall(payload)
    if matches:
        # Last-wins deduplication (matches iterate left-to-right, dict keeps last)
        result = {}
        for key, val in matches:
            result[key] = int(val) if "." not in val else float(val)
        return json.dumps(result, separators=(",", ":"))

    # --- Give up ---------------------------------------------------------------
    print(f"  [WARN] Unrecoverable Wi-Fi payload, substituting {{}}")
    print(f"         Raw value: {payload!r}")
    return "{}"


# ─── Step 1 helper: robust CSV reader ─────────────────────────────────────────

def load_csv_robust(csv_path: str) -> pd.DataFrame:
    """
    The App-1 CSV has a quoting ambiguity: the wifi_payload field is wrapped
    in double-quotes for CSV purposes, but the JSON strings inside also use
    double-quotes without escaping them -- making pandas misparse the field.

    Strategy:
      - Skip comment lines (starting with #)
      - For each data line, split on commas only UP TO the 8th comma
        (the first 8 fields), treating everything after as the raw wifi payload
      - Strip surrounding quotes and whitespace from the wifi field
    """
    EXPECTED_COLS = [
        "timestamp", "step_count",
        "pos_x", "pos_y", "heading",
        "mag_x", "mag_y", "mag_z",
        "wifi_payload",
    ]
    NUM_FIXED_FIELDS = len(EXPECTED_COLS) - 1   # 8 numeric fields before wifi

    rows = []
    header_found = False

    with open(csv_path, encoding="utf-8", errors="replace") as f:
        for raw_line in f:
            line = raw_line.rstrip("\r\n")

            # Skip metadata comment lines
            if line.startswith("#") or not line.strip():
                continue

            # First non-comment line is the header
            if not header_found:
                header_found = True
                continue

            # Split only on the first NUM_FIXED_FIELDS commas
            parts = line.split(",", NUM_FIXED_FIELDS)
            if len(parts) < NUM_FIXED_FIELDS + 1:
                continue  # skip malformed lines

            fixed    = parts[:NUM_FIXED_FIELDS]
            wifi_raw = parts[NUM_FIXED_FIELDS]

            # Strip surrounding CSV-quote characters from the wifi field
            wifi_raw = wifi_raw.strip()
            if wifi_raw.startswith('"') and wifi_raw.endswith('"'):
                wifi_raw = wifi_raw[1:-1]

            rows.append(fixed + [wifi_raw])

    df = pd.DataFrame(rows, columns=EXPECTED_COLS)

    # Cast numeric columns
    for col in ("timestamp", "step_count"):
        df[col] = pd.to_numeric(df[col], errors="coerce").astype("Int64")
    for col in ("pos_x", "pos_y", "heading", "mag_x", "mag_y", "mag_z"):
        df[col] = pd.to_numeric(df[col], errors="coerce")

    return df


# ─── Main pipeline ────────────────────────────────────────────────────────────

def build_localization_db(csv_path: str, db_output_path: str) -> None:

    # ── Guard: input file must exist ─────────────────────────────────────────
    if not os.path.isfile(csv_path):
        print(f"[ERROR] Input file not found: {csv_path}")
        sys.exit(1)

    print("=" * 68)
    print("  MAG-FI  |  Phase 1 Pipeline  --  Starting ...")
    print("=" * 68)

    # -- Step 1: Ingest -------------------------------------------------------
    print(f"\n[1/4] Loading raw mapping trajectory ...")
    print(f"      Source : {csv_path}")

    df = load_csv_robust(csv_path)

    print(f"      Rows loaded   : {len(df)}")
    print(f"      Columns found : {list(df.columns)}")

    # Validate expected columns are present
    required_cols = {
        "timestamp", "step_count",
        "pos_x", "pos_y", "heading",
        "mag_x", "mag_y", "mag_z",
        "wifi_payload",
    }
    missing = required_cols - set(df.columns)
    if missing:
        print(f"[ERROR] CSV is missing expected columns: {missing}")
        sys.exit(1)

    # -- Step 2: Magnetic smoothing -------------------------------------------
    print(f"\n[2/4] Smoothing magnetic noise (centred window = {MAG_SMOOTH_WINDOW}) ...")

    for axis in ("mag_x", "mag_y", "mag_z"):
        raw_std  = df[axis].std()
        df[axis] = (
            df[axis]
            .rolling(window=MAG_SMOOTH_WINDOW, min_periods=1, center=True)
            .mean()
            .round(4)
        )
        smoothed_std = df[axis].std()
        print(f"      {axis}: sigma {raw_std:.4f} -> {smoothed_std:.4f}  "
              f"(noise reduction {100*(1 - smoothed_std/raw_std):.1f}%)")

    # -- Step 3: Wi-Fi JSON normalisation -------------------------------------
    print(f"\n[3/4] Fixing Wi-Fi JSON delimiters (semicolons -> commas) ...")

    df["wifi_payload"] = df["wifi_payload"].apply(fix_wifi_json)

    # Quick sanity-check: count how many rows now parse cleanly
    valid_wifi = df["wifi_payload"].apply(
        lambda s: s != "{}" and len(json.loads(s)) > 0
    ).sum()
    print(f"      Valid Wi-Fi payloads : {valid_wifi} / {len(df)}")

    # -- Step 4: SQLite compilation -------------------------------------------
    print(f"\n[4/4] Compiling SQLite database -> {db_output_path} ...")

    # Remove stale DB so we always start clean
    if os.path.exists(db_output_path):
        os.remove(db_output_path)
        print(f"      Removed existing database to rebuild from scratch.")

    conn   = sqlite3.connect(db_output_path)
    cursor = conn.cursor()

    # Schema – matches the fingerprints contract expected by App 2
    cursor.execute(f"""
        CREATE TABLE IF NOT EXISTS {DB_TABLE} (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp   INTEGER NOT NULL,
            step_count  INTEGER NOT NULL,
            pos_x       REAL    NOT NULL,
            pos_y       REAL    NOT NULL,
            heading     REAL    NOT NULL,
            mag_x       REAL    NOT NULL,
            mag_y       REAL    NOT NULL,
            mag_z       REAL    NOT NULL,
            wifi_json   TEXT    NOT NULL
        )
    """)

    # Bulk insert via executemany for efficiency
    records = [
        (
            int(row["timestamp"]),
            int(row["step_count"]),
            round(float(row["pos_x"]),  2),
            round(float(row["pos_y"]),  2),
            round(float(row["heading"]), 1),
            round(float(row["mag_x"]),  4),
            round(float(row["mag_y"]),  4),
            round(float(row["mag_z"]),  4),
            row["wifi_payload"],          # already cleaned JSON string
        )
        for _, row in df.iterrows()
    ]

    cursor.executemany(
        f"""
        INSERT INTO {DB_TABLE}
            (timestamp, step_count, pos_x, pos_y, heading,
             mag_x, mag_y, mag_z, wifi_json)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        records,
    )

    conn.commit()

    # ── Verification query ────────────────────────────────────────────────────
    row_count = cursor.execute(
        f"SELECT COUNT(*) FROM {DB_TABLE}"
    ).fetchone()[0]

    sample = cursor.execute(
        f"SELECT id, pos_x, pos_y, mag_x, wifi_json FROM {DB_TABLE} LIMIT 3"
    ).fetchall()

    conn.close()

    db_size_kb = os.path.getsize(db_output_path) / 1024

    print(f"\n{'=' * 68}")
    print(f"  SUCCESS: map_database.db is ready for App 2!")
    print(f"{'=' * 68}")
    print(f"  Rows inserted : {row_count}")
    print(f"  DB size       : {db_size_kb:.1f} KB")
    print(f"  Output path   : {os.path.abspath(db_output_path)}")
    print(f"\n  Sample rows (id | pos_x | pos_y | mag_x | wifi_json):")
    for s in sample:
        print(f"    {s[0]:>3} | {s[1]:>6} | {s[2]:>6} | {s[3]:>8} | "
              f"{s[4][:55]}...")
    print()


# ─── Entry point ──────────────────────────────────────────────────────────────

if __name__ == "__main__":
    # Resolve paths relative to *this script's* directory so the script
    # can be run from any working directory.
    SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

    CSV_INPUT  = os.path.join(SCRIPT_DIR, "raw_mapping_1_3_20260521_173715.csv")
    DB_OUTPUT  = os.path.join(SCRIPT_DIR, "map_database.db")

    build_localization_db(CSV_INPUT, DB_OUTPUT)
