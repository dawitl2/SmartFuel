# SmartFuel API

Base URL for local development: `http://localhost:4000`

Most read/write endpoints support an optional source query:

```text
?source=mock
?source=firestore
```

Mock remains the default. Firestore requires Firebase environment variables. Firestore mode is intentionally strict: it does not backfill demo trips, charts, or notifications when the database is empty.

## Health

### `GET /health`

Returns backend service status.

## Dashboard

### `GET /api/dashboard`

Returns the combined dashboard payload:

- car profile
- fuel state
- live vehicle status
- recent trips
- chart series
- statistics
- maintenance records
- notifications

### `GET /api/data-source`

Returns configured data source status for mock and Firestore.

## Fuel

### `GET /api/fuel`

Returns fuel estimate, fuel constants, and refuel event history.

### `POST /api/refuel`

Records a full tank reset or partial refuel.

```json
{
  "eventType": "partial_refuel",
  "litersAdded": 5,
  "note": "Added fuel from dashboard"
}
```

`eventType` must be `full_reset` or `partial_refuel`.

## Telemetry

### `POST /api/telemetry`

Accepts telemetry from the future Raspberry Pi service.

Required header:

```http
X-Device-Token: smartfuel-demo-device-token
```

Example body:

```json
{
  "speedKph": 54,
  "rpm": 2200,
  "sampleSeconds": 30,
  "engineLoadPercent": 48,
  "coolantTempC": 89,
  "engineState": "driving",
  "fuelSensorPercent": 76,
  "fuelSensorState": "descending"
}
```

If `fuelSensorState` is `full` or `fuelSensorPercent` is `98` or higher, SmartFuel treats the sample as a full-tank reset signal.

## Trips

### `GET /api/trips`

Returns recent and active trip summaries.

## Statistics

### `GET /api/statistics`

Returns daily, weekly, monthly distance and driving habit demo data.

## Maintenance

### `GET /api/maintenance`

Returns maintenance records for the demo vehicle.

## Notifications

### `GET /api/notifications`

Returns low fuel, idle, and maintenance notification demo records.
