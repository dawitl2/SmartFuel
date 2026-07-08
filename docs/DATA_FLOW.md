# SmartFuel Data Flow and Firestore Model

This document describes how telemetry moves through SmartFuel when Firestore is enabled, what data each layer owns, and how mock mode can stay available for demos.

## Runtime Modes

```text
Mock Mode
Raspberry Pi mock/provider -> Express API memory store -> Web + Android

Firestore Mode
Raspberry Pi OBD/provider -> Express API or Firestore -> Firestore collections -> Express API -> Web + Android
```

Mock mode stays the default so the project always runs without Firebase keys. Firestore mode is enabled by environment variables and an app setting.

## High-Level Architecture

```mermaid
flowchart TD
  OBD[Vehicle OBD-II Port] --> Adapter[USB OBD-II Adapter]
  Adapter --> Pi[Raspberry Pi Python Service]
  Pi --> Cache[Offline JSONL Cache]
  Pi -->|online telemetry upload| API[Express API]
  Pi -. optional direct write .-> FS[(Cloud Firestore)]
  API -->|mock mode| Mock[In-Memory Demo Store]
  API -->|firestore mode| FS
  FS --> API
  API --> Web[React Web Dashboard]
  API --> Android[Native Android App]
  Android -->|source switch| API
```

## Telemetry Write Flow

```mermaid
sequenceDiagram
  participant Car as Vehicle ECU
  participant Pi as Raspberry Pi
  participant Cache as Offline Cache
  participant API as Express API
  participant FS as Firestore
  participant Apps as Web / Android

  Car->>Pi: OBD-II PID reads
  Pi->>Pi: Normalize speed, RPM, load, fuel sensor, distance increment
  alt network available
    Pi->>API: POST /api/telemetry
    API->>FS: Write telemetry_logs + current_status
    API->>FS: Update fuel_state if Firestore mode
  else offline
    Pi->>Cache: Append telemetry JSONL
  end
  Pi->>API: Retry cached telemetry when network returns
  Apps->>API: GET /api/dashboard?source=firestore
  API->>FS: Read dashboard documents
  FS-->>API: Current vehicle state
  API-->>Apps: Dashboard JSON
```

## Full Tank Reset Logic

The original fuel gauge can still report full, but does not reliably rise after partial refuels. SmartFuel treats a reliable full signal as a reset point.

```mermaid
flowchart TD
  Sample[Telemetry sample arrives] --> Full{fuelSensorState is full<br/>or fuelSensorPercent >= 98}
  Full -->|yes| Reset[Reset distanceSinceFull to 0<br/>fuelUsedLiters to 0<br/>fuelRemainingLiters to tank capacity]
  Full -->|no| Estimate[Estimate fuel from distance + driving intensity]
  Reset --> Store[Write fuel_state + refuel_events]
  Estimate --> Store
```

## Fuel Estimation With Driving Intensity

Base model:

```text
1 liter = 10 km
1 km = 0.1 liters
```

SmartFuel applies a multiplier for aggressive driving:

```text
sampleFuelUsed = distanceIncrementKm * 0.1 * drivingIntensityMultiplier
```

Driving intensity considers:

- high RPM
- high engine load
- low-speed city driving
- idle time

This keeps the model understandable while acknowledging that a short aggressive drive can use more fuel than a calm short drive.

## Firestore Collection Shape

```text
vehicles/{vehicleId}
  name
  make
  model
  year
  status
  tankCapacityLiters
  maxRangeKm
  consumptionLitersPerKm

vehicles/{vehicleId}/runtime/fuel_state
  fuelRemainingLiters
  fuelUsedLiters
  estimatedRangeKm
  fuelPercentage
  lastFullResetAt
  updatedAt

vehicles/{vehicleId}/runtime/current_status
  speedKph
  rpm
  engineState
  drivingIntensity
  engineLoadPercent
  coolantTempC
  batteryVoltage
  estimatedOdometerKm
  tripDistanceKm
  timestamp

vehicles/{vehicleId}/telemetry_logs/{logId}
  recordedAt
  speedKph
  rpm
  distanceIncrementKm
  estimatedOdometerKm
  engineLoadPercent
  coolantTempC
  batteryVoltage
  fuelSensorPercent
  fuelSensorState
  engineState
  obd

vehicles/{vehicleId}/trips/{tripId}
  startedAt
  endedAt
  distanceKm
  drivingSeconds
  idleSeconds
  averageSpeedKph
  maxSpeedKph
  averageRpm
  fuelUsedLiters

vehicles/{vehicleId}/refuel_events/{eventId}
  eventType
  litersAdded
  fuelAfterLiters
  pricePerLiter
  totalCost
  odometerKm
  note
  createdAt

vehicles/{vehicleId}/notifications/{notificationId}
  type
  severity
  title
  body
  isRead
  createdAt
```

An example starter dataset is available in [database/firestore-seed.example.json](../database/firestore-seed.example.json).

## Source Switching

The apps call the backend with a source query:

```text
GET /api/dashboard?source=mock
GET /api/dashboard?source=firestore
```

The Android app has a settings switch. When Firestore mode is enabled, it requests `source=firestore`. If Firestore is not configured, the backend returns a clear configuration error and the app can keep using cached/mock data.

## Required Firebase Environment Variables

```text
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_WEB_API_KEY=your-web-api-key
SMARTFUEL_FIRESTORE_VEHICLE_ID=your-vehicle-id
SMARTFUEL_DATA_SOURCE=mock
```

For production, replace API-key-only access with Firebase Auth or a service-account-backed backend. The current REST adapter is intended to make integration shape-ready without committing secrets.
