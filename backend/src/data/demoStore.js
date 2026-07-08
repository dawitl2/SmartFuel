import {
  applyRefuel,
  calculateFuelState,
  calculateFuelUsedForSample,
  deriveEngineState,
  drivingIntensityMultiplier,
  FUEL_CONSTANTS,
} from '../services/fuelService.js'

const now = new Date('2026-07-08T09:00:00.000Z')

const car = {
  id: 'car-demo-001',
  name: 'Daily Driver',
  make: 'Toyota',
  model: 'Corolla',
  year: 2008,
  status: 'online',
  lastSeenAt: new Date(now.getTime() - 90_000).toISOString(),
}

const telemetryLogs = Array.from({ length: 28 }, (_, index) => {
  const speedPattern = [0, 18, 34, 52, 61, 73, 68, 42, 24, 0, 31, 46, 58, 65]
  const rpmPattern = [780, 1250, 1850, 2250, 2400, 2860, 2600, 1800, 1300, 760, 1600, 2100, 2480, 2700]
  const speedKph = speedPattern[index % speedPattern.length]
  const rpm = rpmPattern[index % rpmPattern.length]
  const recordedAt = new Date(now.getTime() - (27 - index) * 2 * 60_000)
  const distanceIncrementKm = speedKph === 0 ? 0 : Number((speedKph / 30).toFixed(2))

  return {
    id: `tel-${index + 1}`,
    carId: car.id,
    tripId: index < 14 ? 'trip-demo-003' : 'trip-demo-active',
    recordedAt: recordedAt.toISOString(),
    speedKph,
    rpm,
    distanceIncrementKm,
    estimatedOdometerKm: Number((128430 + index * distanceIncrementKm).toFixed(1)),
    engineLoadPercent: speedKph === 0 ? 18 : Math.min(82, 30 + Math.round(speedKph * 0.7)),
    coolantTempC: 86 + (index % 5),
  }
})

const trips = [
  {
    id: 'trip-demo-001',
    carId: car.id,
    startedAt: '2026-07-06T06:42:00.000Z',
    endedAt: '2026-07-06T07:31:00.000Z',
    distanceKm: 28.4,
    drivingSeconds: 2640,
    idleSeconds: 300,
    averageSpeedKph: 38.7,
    maxSpeedKph: 82,
    averageRpm: 2180,
    fuelUsedLiters: 2.84,
  },
  {
    id: 'trip-demo-002',
    carId: car.id,
    startedAt: '2026-07-07T14:18:00.000Z',
    endedAt: '2026-07-07T15:04:00.000Z',
    distanceKm: 22.7,
    drivingSeconds: 2380,
    idleSeconds: 380,
    averageSpeedKph: 34.3,
    maxSpeedKph: 74,
    averageRpm: 2040,
    fuelUsedLiters: 2.27,
  },
  {
    id: 'trip-demo-active',
    carId: car.id,
    startedAt: '2026-07-08T08:34:00.000Z',
    endedAt: null,
    distanceKm: 17.1,
    drivingSeconds: 1440,
    idleSeconds: 180,
    averageSpeedKph: 42.8,
    maxSpeedKph: 73,
    averageRpm: 2240,
    fuelUsedLiters: 1.71,
  },
]

let totalDistanceSinceResetKm = 132
let manualFuelCreditLiters = 0
let drivingAdjustmentLiters = 0
let fuelState = {
  ...calculateFuelState(totalDistanceSinceResetKm, manualFuelCreditLiters, drivingAdjustmentLiters),
  lastFullResetAt: '2026-07-03T06:30:00.000Z',
  updatedAt: now.toISOString(),
}

const refuelEvents = [
  {
    id: 'refuel-demo-001',
    carId: car.id,
    eventType: 'full_reset',
    litersAdded: 40,
    fuelAfterLiters: 40,
    odometerKm: 128298,
    note: 'Full tank reset after filling the tank.',
    createdAt: '2026-07-03T06:30:00.000Z',
  },
]

const maintenanceRecords = [
  {
    id: 'maintenance-demo-001',
    title: 'Engine Oil Change',
    description: 'Next oil service based on estimated odometer.',
    dueOdometerKm: 129000,
    dueAt: '2026-08-15',
    status: 'scheduled',
  },
  {
    id: 'maintenance-demo-002',
    title: 'OBD Adapter Inspection',
    description: 'Check USB cable, Raspberry Pi service logs, and adapter stability.',
    dueOdometerKm: null,
    dueAt: '2026-07-20',
    status: 'scheduled',
  },
]

const notifications = [
  {
    id: 'notification-demo-001',
    type: 'low_fuel',
    severity: 'warning',
    title: 'Fuel watch',
    body: 'Estimated fuel is below 70%. Demo threshold is intentionally high for visibility.',
    isRead: false,
    createdAt: '2026-07-08T08:58:00.000Z',
  },
  {
    id: 'notification-demo-002',
    type: 'long_idle',
    severity: 'info',
    title: 'Idle time detected',
    body: 'The current trip has 3 minutes of idle time.',
    isRead: false,
    createdAt: '2026-07-08T08:47:00.000Z',
  },
]

function chartSeries() {
  return telemetryLogs.slice(-18).map((log) => ({
    time: new Date(log.recordedAt).toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
    }),
    speed: log.speedKph,
    rpm: log.rpm,
    fuel: Number(Math.max(0, fuelState.fuelPercentage - (18 - telemetryLogs.slice(-18).indexOf(log)) * 0.18).toFixed(1)),
  }))
}

function statistics() {
  return {
    dailyDistance: [
      { day: 'Thu', km: 31 },
      { day: 'Fri', km: 18 },
      { day: 'Sat', km: 45 },
      { day: 'Sun', km: 12 },
      { day: 'Mon', km: 28 },
      { day: 'Tue', km: 23 },
      { day: 'Wed', km: 17 },
    ],
    weeklyDistance: [
      { week: 'W24', km: 168 },
      { week: 'W25', km: 204 },
      { week: 'W26', km: 143 },
      { week: 'W27', km: 174 },
    ],
    monthlyDistance: [
      { month: 'Mar', km: 612 },
      { month: 'Apr', km: 705 },
      { month: 'May', km: 588 },
      { month: 'Jun', km: 760 },
      { month: 'Jul', km: 174 },
    ],
    habits: [
      { label: 'Cruise', value: 62 },
      { label: 'Idle', value: 13 },
      { label: 'High RPM', value: 8 },
      { label: 'City Stops', value: 17 },
    ],
    averageFuelUsageLitersPer100Km: 10,
  }
}

export function getDashboard() {
  const latest = telemetryLogs.at(-1)
  const trip = trips.find((item) => item.id === 'trip-demo-active')

  return {
    car,
    source: 'mock',
    settings: {
      tankCapacityLiters: FUEL_CONSTANTS.tankCapacityLiters,
      maxRangeKm: FUEL_CONSTANTS.maxRangeKm,
      lowFuelNotificationPercent: 70,
      firestoreReady: false,
    },
    fuel: fuelState,
    liveStatus: {
      speedKph: latest.speedKph,
      rpm: latest.rpm,
      engineState: deriveEngineState(latest),
      drivingIntensity: drivingIntensityMultiplier(latest),
      coolantTempC: latest.coolantTempC,
      engineLoadPercent: latest.engineLoadPercent,
      estimatedOdometerKm: latest.estimatedOdometerKm,
      tripDistanceKm: trip.distanceKm,
      drivingSeconds: trip.drivingSeconds,
      idleSeconds: trip.idleSeconds,
      averageSpeedKph: trip.averageSpeedKph,
      maxSpeedKph: trip.maxSpeedKph,
      averageRpm: trip.averageRpm,
      timestamp: latest.recordedAt,
    },
    trips: trips.slice().reverse(),
    charts: chartSeries(),
    statistics: statistics(),
    maintenance: maintenanceRecords,
    notifications: buildNotifications(),
  }
}

export function getFuel() {
  return {
    ...fuelState,
    refuelEvents: refuelEvents.slice().reverse(),
    constants: FUEL_CONSTANTS,
  }
}

export function getTrips() {
  return trips.slice().reverse()
}

export function getStatistics() {
  return statistics()
}

export function getMaintenance() {
  return maintenanceRecords
}

export function getNotifications() {
  return buildNotifications()
}

export function addTelemetry(payload) {
  const previous = telemetryLogs.at(-1)
  const recordedAt = payload.timestamp || new Date().toISOString()
  const distanceIncrementKm =
    payload.distanceIncrementKm ??
    Number(Math.max(0, ((payload.speedKph || 0) / 3600) * (payload.sampleSeconds || 30)).toFixed(3))

  const log = {
    id: `tel-${telemetryLogs.length + 1}`,
    carId: car.id,
    tripId: payload.tripId || 'trip-demo-active',
    recordedAt,
    speedKph: payload.speedKph,
    rpm: payload.rpm,
    distanceIncrementKm,
    estimatedOdometerKm: payload.estimatedOdometerKm ?? Number(((previous?.estimatedOdometerKm || 128430) + distanceIncrementKm).toFixed(2)),
    engineLoadPercent: payload.engineLoadPercent ?? null,
    coolantTempC: payload.coolantTempC ?? null,
    batteryVoltage: payload.batteryVoltage ?? null,
    latitude: payload.latitude ?? null,
    longitude: payload.longitude ?? null,
    engineState: payload.engineState ?? deriveEngineState(payload),
    fuelSensorPercent: payload.fuelSensorPercent ?? null,
    fuelSensorState: payload.fuelSensorState ?? null,
    obd: payload.obd ?? null,
  }

  telemetryLogs.push(log)

  if (payload.fuelSensorState === 'full' || payload.fuelSensorPercent >= 98) {
    totalDistanceSinceResetKm = 0
    manualFuelCreditLiters = 0
    drivingAdjustmentLiters = 0
    fuelState.lastFullResetAt = recordedAt
    refuelEvents.push({
      id: `refuel-demo-${refuelEvents.length + 1}`,
      carId: car.id,
      eventType: 'full_reset',
      litersAdded: FUEL_CONSTANTS.tankCapacityLiters,
      fuelAfterLiters: FUEL_CONSTANTS.tankCapacityLiters,
      odometerKm: log.estimatedOdometerKm,
      note: 'Automatic full-tank reset from fuel sensor full signal.',
      createdAt: recordedAt,
    })
  }

  const sampleFuelUsed = calculateFuelUsedForSample(log)
  const baseFuelUsed = distanceIncrementKm * FUEL_CONSTANTS.consumptionLitersPerKm
  drivingAdjustmentLiters += Math.max(0, sampleFuelUsed - baseFuelUsed)
  totalDistanceSinceResetKm += distanceIncrementKm
  fuelState = {
    ...calculateFuelState(totalDistanceSinceResetKm, manualFuelCreditLiters, drivingAdjustmentLiters),
    lastFullResetAt: fuelState.lastFullResetAt,
    updatedAt: recordedAt,
  }

  const activeTrip = trips.find((item) => item.id === 'trip-demo-active')
  if (activeTrip) {
    activeTrip.distanceKm = Number((activeTrip.distanceKm + distanceIncrementKm).toFixed(2))
    activeTrip.maxSpeedKph = Math.max(activeTrip.maxSpeedKph, payload.speedKph)
    activeTrip.fuelUsedLiters = Number((activeTrip.distanceKm * FUEL_CONSTANTS.consumptionLitersPerKm).toFixed(2))
  }

  return log
}

export function addRefuel({ eventType, litersAdded = 0, note = '' }) {
  if (eventType === 'full_reset') {
    totalDistanceSinceResetKm = 0
    manualFuelCreditLiters = 0
    drivingAdjustmentLiters = 0
  } else {
    manualFuelCreditLiters += litersAdded
  }

  const applied = applyRefuel(fuelState.fuelRemainingLiters, litersAdded, eventType)
  fuelState = {
    tankCapacityLiters: FUEL_CONSTANTS.tankCapacityLiters,
    consumptionLitersPerKm: FUEL_CONSTANTS.consumptionLitersPerKm,
    fuelUsedLiters: eventType === 'full_reset' ? 0 : fuelState.fuelUsedLiters,
    fuelRemainingLiters: applied.fuelAfterLiters,
    estimatedRangeKm: applied.estimatedRangeKm,
    fuelPercentage: applied.fuelPercentage,
    lastFullResetAt: eventType === 'full_reset' ? new Date().toISOString() : fuelState.lastFullResetAt,
    updatedAt: new Date().toISOString(),
  }

  const event = {
    id: `refuel-demo-${refuelEvents.length + 1}`,
    carId: car.id,
    eventType,
    litersAdded: eventType === 'full_reset' ? FUEL_CONSTANTS.tankCapacityLiters : litersAdded,
    fuelAfterLiters: applied.fuelAfterLiters,
    odometerKm: telemetryLogs.at(-1)?.estimatedOdometerKm,
    note,
    createdAt: new Date().toISOString(),
  }

  refuelEvents.push(event)
  return event
}

function buildNotifications() {
  const dynamic = []

  if (fuelState.fuelPercentage <= 70) {
    dynamic.push({
      id: 'notification-low-fuel-live',
      type: 'low_fuel',
      severity: fuelState.fuelPercentage <= 15 ? 'critical' : 'warning',
      title: 'Fuel below 70%',
      body: `Estimated fuel is ${fuelState.fuelPercentage}%. The Android app can push a local low-fuel notification below 70%.`,
      isRead: false,
      createdAt: fuelState.updatedAt,
    })
  }

  const latest = telemetryLogs.at(-1)
  if (latest?.rpm > 4200) {
    dynamic.push({
      id: 'notification-high-rpm-live',
      type: 'high_rpm',
      severity: 'warning',
      title: 'High RPM detected',
      body: `Latest sample reported ${latest.rpm} rpm. Aggressive driving increases estimated fuel use.`,
      isRead: false,
      createdAt: latest.recordedAt,
    })
  }

  return [...dynamic, ...notifications]
}
