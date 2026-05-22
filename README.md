# 🧭 Mag-Fi: Adaptive Indoor Localization System

**Built by Team Antigravity**

Mag-Fi is a zero-infrastructure Indoor Positioning System (IPS) that provides real-time, room-level navigation using standard smartphone sensors. By eliminating the need for expensive Bluetooth beacons or external hardware, Mag-Fi relies on a hybrid fusion of Pedestrian Dead Reckoning (PDR), ambient Wi-Fi fingerprinting, and magnetic anomaly matching.

---

## 🏗️ System Architecture

The project is split into a robust three-part pipeline:

1. **Mag-Fi Mapper (App 1):** A native Android data-collection utility that utilizes continuous trajectory mapping to generate linked spatial datasets of building environments. (Step detection threshold: $1.0\text{ m/s}^2$)
2. **The Python Bridge:** An offline data science pipeline that cleans sensor noise, standardizes JSON payloads, and compiles the raw trajectories into a highly optimized SQLite database. (Moving Average Filter window = 5)
3. **Mag-Fi Navigator (App 2):** The end-user client that executes a 3-layer localization fusion loop and renders real-time navigation using Dijkstra's algorithm on a custom 2D canvas.

---

## 🧮 Mathematical Models & Core Engines

### 1. Kinematic Step Detection & PDR
Movement tracking operates independently of GPS. The system identifies footsteps by vectorizing tri-axial accelerometer data and applying a dynamic threshold filter above a rolling mean. 

Relative spatial updates are calculated via:
* $x_t = x_{t-1} + L \cdot \cos(\theta)$
* $y_t = y_{t-1} + L \cdot \sin(\theta)$

*(Where $L$ is a fixed step length of 0.72m, and $\theta$ is the absolute heading)*

### 2. Absolute Heading Estimation
Orientation is derived by fusing Magnetometer and Gyroscope data. To prevent $360^\circ / 0^\circ$ wrap-around averaging artifacts, the system utilizes circular moving averages:
* $\theta = \text{atan2}(B_y, B_x)$

### 3. The 3-Layer Fusion Localization Loop (App 2)
To eliminate the accumulated drift inherent in dead reckoning, the Navigator app executes a real-time correction loop:
* **Layer 1 (Coarse Filter):** Scans ambient Wi-Fi to establish the user's macro-zone, drastically reducing the database search space.
* **Layer 2 (Fine Match):** Computes the Euclidean distance (Sum of Squared Differences) between live magnetic vectors and the database candidates.
  $$SSD = \sqrt{(B_{x1} - B_{x2})^2 + (B_{y1} - B_{y2})^2 + (B_{z1} - B_{z2})^2}$$
* **Layer 3 (Drift Correction):** Executes a K-Nearest Neighbors (KNN) algorithm ($K=3$). If the magnetic match confidence falls below the strict $8\mu T$ threshold, the PDR coordinate "snaps" to the database coordinate, completely erasing spatial drift.

---

## 🚀 Setup & Installation

### Prerequisites
* Minimum SDK 26 (Android 8.0)
* Android Studio (Gradle 8.11.1)
* Python 3.9+ (with Pandas, NumPy)

### Phase 1: Data Collection
1. Install `MagFiMapper.apk` on a physical device.
2. Turn **Off** "Wi-Fi scan throttling" in Android Developer Options.
3. Launch the app, calibrate the compass for 2 seconds, and walk the target floor.
4. Export the `raw_mapping.csv` file.

### Phase 2: Database Generation
1. Place the CSV file in the Python pipeline directory.
2. Run `python build_map_db.py`.
3. The script will smooth magnetic noise (Window=5), fix Wi-Fi JSON delimiters, and output `map_database.db`.

### Phase 3: Navigation
1. Copy `map_database.db` into `MagFiNavigator/app/src/main/assets/databases/`.
2. Place the floor blueprint image into `assets/floor_plan.png`.
3. Update the dual-axis pixel calibration in `FloorPlanManager.kt` to match the image dimensions (SCALE_X: 49.36 px/m, SCALE_Y: 31.875 px/m).
4. Compile and install `MagFiNavigator.apk`.

---

## 🗺️ Routing Graph (Block C Implementation)

The current Navigator prototype utilizes a custom routing engine based on Dijkstra's Algorithm, specifically configured for Block C.
* **Nodes:** 20 (11 corridor waypoints, 9 room destinations)
* **Edges:** 19 connected paths
* **Features:** Includes a stale-entry guard to dynamically recalculate paths if a user overshoots a physical door.

---

## 🛠️ Tech Stack
* **Language:** Kotlin, Python
* **Frameworks/Libraries:** Android SDK, AndroidX Navigation, SQLiteAssetHelper, Pandas
* **UI/UX:** Custom `MapCanvasView` with ScaleGestureDetector (Pinch-to-Zoom), Material 3 Dark Theme
