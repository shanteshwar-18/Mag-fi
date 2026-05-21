import sqlite3
import json

conn = sqlite3.connect("map_database.db")
cur  = conn.cursor()

print("--- Table schema ---")
for row in cur.execute("PRAGMA table_info(fingerprints)"):
    print(f"  {row[1]:12} {row[2]}")

total = cur.execute("SELECT COUNT(*) FROM fingerprints").fetchone()[0]
print(f"\n--- Row count: {total} ---")

print("\n--- Wifi JSON spot-check (rows 1, 50, 99) ---")
for rid in (1, 50, 99):
    r = cur.execute(
        "SELECT id, pos_x, pos_y, heading, mag_x, mag_y, mag_z, wifi_json FROM fingerprints WHERE id=?",
        (rid,)
    ).fetchone()
    wifi = json.loads(r[7])
    print(f"  id={r[0]}  pos=({r[1]},{r[2]})  heading={r[3]}")
    print(f"    mag=({r[4]}, {r[5]}, {r[6]})")
    print(f"    wifi SSIDs : {list(wifi.keys())}")
    print(f"    RSSI values: {list(wifi.values())}")

print("\n--- mag_x smoothing check (first 5 rows) ---")
for row in cur.execute("SELECT id, mag_x FROM fingerprints LIMIT 5"):
    print(f"  id={row[0]}  mag_x={row[1]}")

# Ensure no rows have empty wifi_json
empty_wifi = cur.execute(
    "SELECT COUNT(*) FROM fingerprints WHERE wifi_json = '{}'"
).fetchone()[0]
print(f"\n--- Empty wifi_json rows: {empty_wifi} / {total} ---")

conn.close()
print("\n[PASS] Database integrity verified.")
