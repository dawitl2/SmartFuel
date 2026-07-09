# SmartFuel

SmartFuel is an end-to-end vehicle telemetry and fuel estimation system built for a car with a broken physical fuel gauge. It collects vehicle data, estimates fuel remaining, stores trip and maintenance history, and presents the data through a web dashboard and a native Android app.

| Web Dashboard | Android App |
|---|---|
| <img src="screenshot1%28Desktop%29.png" alt="SmartFuel web dashboard" height="360"> | <img src="screenshot2%28phone%29.png" alt="SmartFuel Android app" height="360"> |

The project is designed like a commercial IoT product: edge telemetry, Firestore-ready database sync, authenticated backend ingestion, REST APIs, responsive web UI, native mobile UI, offline-aware telemetry upload, and a path toward real OBD-II hardware integration.

## What It Does

- Estimates remaining fuel from distance driven.
- Tracks fuel used, fuel remaining, fuel percentage, and remaining range.
- Supports full tank reset and partial refuel events.
- Displays live vehicle status, trip history, fuel analytics, maintenance, and notifications.
- Accepts telemetry from a Raspberry Pi service.
- Provides the same backend API to the web app and Android app.
- Includes Firestore-ready data flow, collection design, and source switching.

## System Flow

```text
Vehicle OBD-II Port
  -> USB OBD-II Adapter
  -> Raspberry Pi
  -> Python Telemetry Service
  -> 4G / Wi-Fi Internet
  -> Node.js Express API
  -> Firebase Firestore
  -> React Web Dashboard + Native Android App
```

For the current development build, mock telemetry remains available by default. Firestore mode can be enabled from the mobile settings switch and by backend environment variables. When Firestore mode is selected, the app requests database data only; it does not silently fall back to demo trips, charts, or notifications.

<img src="Frame1000002306.png.png" alt="" height="360">

## Fuel Model

SmartFuel uses a simple explainable model for the first production version:

- Full tank: `40 L`
- Maximum range: `400 km`
- Consumption: `1 L / 10 km`
- Per kilometer: `0.1 L`

Calculated values:

- `fuelUsedLiters = distanceDrivenKm * 0.1`
- `fuelRemainingLiters = tankCapacityLiters - fuelUsedLiters + refuels`
- `estimatedRangeKm = fuelRemainingLiters / 0.1`
- `fuelPercentage = fuelRemainingLiters / tankCapacityLiters * 100`

This model is intentionally transparent. SmartFuel also applies a driving-intensity multiplier from RPM, speed, engine load, and idle state so short aggressive trips can consume more estimated fuel than calm trips.

## Tech Stack

| Layer | Technology | Why It Was Used |
|---|---|---|
| Vehicle interface | OBD-II USB adapter | Standard diagnostic interface available on most cars. |
| Edge device | Raspberry Pi | Reliable low-cost Linux device that can run continuously in a vehicle. |
| Telemetry service | Python | Strong hardware/serial ecosystem and simple background service deployment. |
| Backend | Node.js + Express | Lightweight REST API, fast iteration, and easy integration with web/mobile clients. |
| Validation | Zod | Runtime request validation for telemetry and refuel payloads. |
| Database | Firebase Firestore | Realtime-friendly cloud document database for vehicle telemetry, current state, mobile reads, and future notifications. |
| Web frontend | React + Vite | Fast modern web development and component-based dashboard UI. |
| Styling | Tailwind CSS | Rapid responsive UI with consistent design tokens. |
| Charts | Recharts | Simple React charting for speed, RPM, distance, and fuel history. |
| Mobile app | Kotlin + Jetpack Compose | Native Android performance and modern declarative UI without React Native. |
| Communication | REST over HTTP | Simple MVP protocol shared by Raspberry Pi, web, and Android clients. |
| Optional relational design | PostgreSQL schema | Kept as a normalized reference for future reporting or analytics-heavy deployments. |
| Future messaging | MQTT | Better fit for realtime IoT streams and unreliable networks. |

## Repository Structure

```text
SmartFuel/
  backend/        Node.js Express API, demo data, telemetry ingestion, fuel logic
  database/       Optional PostgreSQL schema and seed data reference
  frontend/       React, Vite, Tailwind, Recharts web dashboard
  android/        Native Kotlin + Jetpack Compose Android app
  raspberry-pi/   Python telemetry uploader with offline cache and retry
  docs/           API documentation
```

## Firestore Data Flow

SmartFuel is ready for Firestore integration without committing Firebase keys.

```text
Raspberry Pi reads OBD-II data
  -> normalizes telemetry
  -> uploads to Express API or Firestore
  -> Firestore stores telemetry_logs and runtime current_status
  -> web and Android request dashboard data
  -> Android settings switch can move from mock to Firestore mode
```

Required environment variables:

```text
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_WEB_API_KEY=your-web-api-key
SMARTFUEL_FIRESTORE_VEHICLE_ID=your-vehicle-id
SMARTFUEL_DATA_SOURCE=mock
```

The full diagrams and document model are in [docs/DATA_FLOW.md](docs/DATA_FLOW.md).

## Backend

The backend is responsible for:

- Receiving telemetry from the Raspberry Pi.
- Authenticating telemetry devices with `X-Device-Token`.
- Validating request bodies.
- Calculating fuel estimates.
- Recording refuel events.
- Serving dashboard, fuel, trips, statistics, maintenance, and notifications APIs.
- Providing a future-ready boundary for Firebase Auth, Firestore, and multi-vehicle support.

Important endpoints:

- `GET /health`
- `GET /api/dashboard`
- `GET /api/fuel`
- `GET /api/trips`
- `GET /api/statistics`
- `GET /api/maintenance`
- `GET /api/notifications`
- `POST /api/telemetry`
- `POST /api/refuel`

Full API documentation is in [docs/API.md](docs/API.md).

## Database Design

The primary cloud database path is Firebase Firestore:

- `vehicles/{vehicleId}`
- `vehicles/{vehicleId}/runtime/fuel_state`
- `vehicles/{vehicleId}/runtime/current_status`
- `vehicles/{vehicleId}/telemetry_logs/{logId}`
- `vehicles/{vehicleId}/trips/{tripId}`
- `vehicles/{vehicleId}/refuel_events/{eventId}`
- `vehicles/{vehicleId}/maintenance_records/{recordId}`
- `vehicles/{vehicleId}/notifications/{notificationId}`

The repository also includes an optional PostgreSQL reference schema for teams that later want relational reporting or exports.

The PostgreSQL schema is normalized:

- `users`
- `cars`
- `telemetry_logs`
- `trips`
- `fuel_state`
- `refuel_events`
- `maintenance_records`
- `settings`
- `notifications`

The schema includes primary keys, foreign keys, enums, constraints, and indexes for common query paths such as telemetry by car/time, trips by car/start date, unread notifications, and maintenance status.

Files:

- [database/schema.sql](database/schema.sql)
- [database/seed.sql](database/seed.sql)

## Web Dashboard

The React dashboard provides:

- Large fuel, range, speed, and RPM metric cards.
- Live vehicle status.
- Fuel estimation controls.
- Speed and RPM charts.
- Fuel estimate history.
- Daily distance chart.
- Trip history table.
- Maintenance records.
- Notifications.
- Light and dark mode.
- Responsive layout for desktop and mobile browsers.

## Android App

The Android app is fully native:

- Kotlin
- Jetpack Compose
- Native HTTP client using Android/Java networking
- SharedPreferences cache for the last successful dashboard payload
- Same REST API contract as the web app
- Settings tab with Mock/Firestore switch
- Low-fuel phone notification below 70%
- Overview, Stats, Trips, and Settings tabs
- Collapsible fuel controls so full reset/refuel actions stay out of the way

Build and install helper:

```powershell
cd android
.\install-debug.ps1
```

The generated APK is:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Raspberry Pi Telemetry Service

The Python service is structured like the real edge client:

- Reads telemetry from a provider.
- Generates realistic mock OBD-style values in the current version.
- Calculates distance increments.
- Uploads telemetry to the backend.
- Can be configured for Firestore upload mode.
- Caches failed uploads locally.
- Retries cached uploads when the backend is reachable again.
- Includes a systemd service template for boot startup.

Run locally:

```powershell
cd raspberry-pi
python -m smartfuel_pi.telemetry_service
```

The production provider can later be replaced with `python-OBD` while keeping the same upload payload.

## Running Locally

Install all JavaScript dependencies:

```powershell
npm run install:all
```

Run backend and web frontend:

```powershell
npm run dev
```

Build the web app:

```powershell
npm run build
```

Install Android debug app:

```powershell
.\android\install-debug.ps1
```

Run Raspberry Pi mock telemetry:

```powershell
cd raspberry-pi
python -m smartfuel_pi.telemetry_service
```

## Security Design

Current security measures:

- Device token authentication for telemetry ingestion.
- Request validation with Zod.
- Helmet HTTP security headers.
- CORS restricted to the web frontend origin.
- Firestore-ready document model for valid telemetry and fuel values.
- Environment-based configuration.

## Production Roadmap

Planned improvements:

- Real refuel cost tracking: liters, price per liter, total cost, station, odometer, and monthly fuel spending analysis.
- Fuel economy summaries by trip, week, month, and driving style.
- Multiple cars per user.
- GPS location and route history.
- Diagnostic trouble codes.
- Battery voltage monitoring.
- Predictive maintenance.
- Fuel consumption prediction.

## Why This Project Matters

SmartFuel solves a real vehicle problem with a full-stack IoT architecture. It demonstrates practical software engineering across hardware integration, backend API design, database modeling, frontend dashboards, native mobile development, offline handling, and production planning.

The result is not just a UI mockup. It is a working multi-client system where the web app, Android app, backend, database schema, and Raspberry Pi service all share one consistent telemetry and fuel estimation model.
