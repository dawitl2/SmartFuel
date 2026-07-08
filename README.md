# SmartFuel

SmartFuel is an end-to-end vehicle telemetry and fuel estimation system built for a car with a broken physical fuel gauge. It collects vehicle data, estimates fuel remaining, stores trip and maintenance history, and presents the data through a web dashboard and a native Android app.

The project is designed like a commercial IoT product: edge telemetry, authenticated backend ingestion, PostgreSQL data modeling, REST APIs, responsive web UI, native mobile UI, offline-aware telemetry upload, and a path toward real OBD-II hardware integration.

## What It Does

- Estimates remaining fuel from distance driven.
- Tracks fuel used, fuel remaining, fuel percentage, and remaining range.
- Supports full tank reset and partial refuel events.
- Displays live vehicle status, trip history, fuel analytics, maintenance, and notifications.
- Accepts telemetry from a Raspberry Pi service.
- Provides the same backend API to the web app and Android app.
- Includes a normalized Supabase PostgreSQL schema for production storage.

## System Flow

```text
Vehicle OBD-II Port
  -> USB OBD-II Adapter
  -> Raspberry Pi
  -> Python Telemetry Service
  -> 4G / Wi-Fi Internet
  -> Node.js Express API
  -> PostgreSQL / Supabase
  -> React Web Dashboard + Native Android App
```

For the current development build, the Raspberry Pi service uses mock telemetry and the backend uses demo in-memory data. The interfaces are shaped so the mock layer can be replaced with real OBD-II and Supabase persistence without redesigning the clients.

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

This model is intentionally transparent. Future versions can improve accuracy with real mass air flow, fuel rate PID values, GPS distance validation, driver profiles, and ML-based consumption prediction.

## Tech Stack

| Layer | Technology | Why It Was Used |
|---|---|---|
| Vehicle interface | OBD-II USB adapter | Standard diagnostic interface available on most cars. |
| Edge device | Raspberry Pi | Reliable low-cost Linux device that can run continuously in a vehicle. |
| Telemetry service | Python | Strong hardware/serial ecosystem and simple background service deployment. |
| Backend | Node.js + Express | Lightweight REST API, fast iteration, and easy integration with web/mobile clients. |
| Validation | Zod | Runtime request validation for telemetry and refuel payloads. |
| Database | Supabase PostgreSQL | Managed PostgreSQL, authentication support, relational integrity, and future realtime features. |
| Web frontend | React + Vite | Fast modern web development and component-based dashboard UI. |
| Styling | Tailwind CSS | Rapid responsive UI with consistent design tokens. |
| Charts | Recharts | Simple React charting for speed, RPM, distance, and fuel history. |
| Mobile app | Kotlin + Jetpack Compose | Native Android performance and modern declarative UI without React Native. |
| Communication | REST over HTTP | Simple MVP protocol shared by Raspberry Pi, web, and Android clients. |
| Future messaging | MQTT | Better fit for realtime IoT streams and unreliable networks. |

## Repository Structure

```text
SmartFuel/
  backend/        Node.js Express API, demo data, telemetry ingestion, fuel logic
  database/       Supabase PostgreSQL schema and seed data
  frontend/       React, Vite, Tailwind, Recharts web dashboard
  android/        Native Kotlin + Jetpack Compose Android app
  raspberry-pi/   Python telemetry uploader with offline cache and retry
  docs/           API documentation
```

## Backend

The backend is responsible for:

- Receiving telemetry from the Raspberry Pi.
- Authenticating telemetry devices with `X-Device-Token`.
- Validating request bodies.
- Calculating fuel estimates.
- Recording refuel events.
- Serving dashboard, fuel, trips, statistics, maintenance, and notifications APIs.
- Providing a future-ready boundary for Supabase Auth and multi-vehicle support.

Local backend URL:

```text
http://localhost:4000
```

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

The PostgreSQL schema is normalized and prepared for Supabase:

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

Local web URL:

```text
http://127.0.0.1:5173
```

## Android App

The Android app is fully native:

- Kotlin
- Jetpack Compose
- Native HTTP client using Android/Java networking
- SharedPreferences cache for the last successful dashboard payload
- Same REST API contract as the web app

During USB development, the app calls:

```text
http://127.0.0.1:4000
```

Use adb reverse so the phone can reach the computer backend:

```powershell
adb reverse tcp:4000 tcp:4000
```

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
- Generates mock OBD-style values in the current version.
- Calculates distance increments.
- Uploads telemetry to the backend.
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
- Clear database constraints for valid telemetry and fuel values.
- Environment-based configuration.

Production security roadmap:

- Supabase Auth for users.
- Hashed device tokens per vehicle.
- Row Level Security policies in Supabase.
- HTTPS-only backend.
- Refresh-token based mobile sessions.
- Rate limiting for telemetry ingestion.
- Audit logs for refuel and maintenance events.
- Secrets stored in platform-managed secret stores.

## Production Roadmap

Planned improvements:

- Real OBD-II adapter integration on Raspberry Pi.
- Supabase persistence instead of in-memory demo data.
- Supabase Auth email login and password reset.
- Multiple cars per user.
- GPS location and route history.
- Diagnostic trouble codes.
- Battery voltage monitoring.
- Push notifications.
- MQTT telemetry streaming.
- Predictive maintenance.
- Fuel consumption prediction.
- PDF/CSV reports.
- Admin and fleet dashboard.

## Why This Project Matters

SmartFuel solves a real vehicle problem with a full-stack IoT architecture. It demonstrates practical software engineering across hardware integration, backend API design, database modeling, frontend dashboards, native mobile development, offline handling, and production planning.

The result is not just a UI mockup. It is a working multi-client system where the web app, Android app, backend, database schema, and Raspberry Pi service all share one consistent telemetry and fuel estimation model.
