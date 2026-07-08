import { config } from '../config.js'
import { addTelemetry as addMockTelemetry, getDashboard as getMockDashboard } from './demoStore.js'

const databaseId = '(default)'

function isConfigured() {
  return Boolean(config.firebaseProjectId && config.firebaseWebApiKey && config.firestoreVehicleId)
}

function baseUrl() {
  return `https://firestore.googleapis.com/v1/projects/${config.firebaseProjectId}/databases/${databaseId}/documents`
}

function documentUrl(path) {
  return `${baseUrl()}/${path}?key=${config.firebaseWebApiKey}`
}

function collectionUrl(path, params = '') {
  const suffix = params ? `&${params}` : ''
  return `${baseUrl()}/${path}?key=${config.firebaseWebApiKey}${suffix}`
}

function toFirestoreValue(value) {
  if (value === null || value === undefined) return { nullValue: null }
  if (typeof value === 'boolean') return { booleanValue: value }
  if (typeof value === 'number') {
    return Number.isInteger(value) ? { integerValue: value } : { doubleValue: value }
  }
  if (value instanceof Date) return { timestampValue: value.toISOString() }
  if (Array.isArray(value)) return { arrayValue: { values: value.map(toFirestoreValue) } }
  if (typeof value === 'object') {
    return {
      mapValue: {
        fields: Object.fromEntries(Object.entries(value).map(([key, item]) => [key, toFirestoreValue(item)])),
      },
    }
  }
  return { stringValue: String(value) }
}

function fromFirestoreValue(value) {
  if ('stringValue' in value) return value.stringValue
  if ('integerValue' in value) return Number(value.integerValue)
  if ('doubleValue' in value) return Number(value.doubleValue)
  if ('booleanValue' in value) return value.booleanValue
  if ('timestampValue' in value) return value.timestampValue
  if ('nullValue' in value) return null
  if ('arrayValue' in value) return (value.arrayValue.values || []).map(fromFirestoreValue)
  if ('mapValue' in value) return fromFirestoreFields(value.mapValue.fields || {})
  return null
}

function toFirestoreFields(payload) {
  return {
    fields: Object.fromEntries(Object.entries(payload).map(([key, value]) => [key, toFirestoreValue(value)])),
  }
}

function fromFirestoreFields(fields = {}) {
  return Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, fromFirestoreValue(value)]))
}

async function firestoreFetch(url, options = {}) {
  if (!isConfigured()) {
    const error = new Error('Firestore is not configured. Set FIREBASE_PROJECT_ID, FIREBASE_WEB_API_KEY, and SMARTFUEL_FIRESTORE_VEHICLE_ID.')
    error.statusCode = 503
    throw error
  }

  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...options,
  })

  const body = await response.json().catch(() => ({}))
  if (!response.ok) {
    const error = new Error(body.error?.message || `Firestore request failed: HTTP ${response.status}`)
    error.statusCode = response.status
    throw error
  }
  return body
}

async function readDocument(path) {
  const document = await firestoreFetch(documentUrl(path))
  return fromFirestoreFields(document.fields || {})
}

async function listCollection(path, params = '') {
  const body = await firestoreFetch(collectionUrl(path, params))
  return (body.documents || []).map((document) => ({
    id: document.name.split('/').at(-1),
    ...fromFirestoreFields(document.fields || {}),
  }))
}

async function createDocument(path, payload) {
  const body = await firestoreFetch(collectionUrl(path), {
    method: 'POST',
    body: JSON.stringify(toFirestoreFields(payload)),
  })
  return {
    id: body.name?.split('/').at(-1),
    ...payload,
  }
}

async function patchDocument(path, payload) {
  const fields = Object.keys(payload).map((key) => `updateMask.fieldPaths=${encodeURIComponent(key)}`).join('&')
  const separator = documentUrl(path).includes('?') ? '&' : '?'
  await firestoreFetch(`${documentUrl(path)}${separator}${fields}`, {
    method: 'PATCH',
    body: JSON.stringify(toFirestoreFields(payload)),
  })
  return payload
}

function vehiclePath() {
  return `vehicles/${config.firestoreVehicleId}`
}

export async function getDashboard() {
  const car = await readDocument(vehiclePath())
  const fuel = await readDocument(`${vehiclePath()}/runtime/fuel_state`)
  const liveStatus = await readDocument(`${vehiclePath()}/runtime/current_status`)
  const telemetry = await listCollection(`${vehiclePath()}/telemetry_logs`, 'pageSize=24&orderBy=recordedAt%20desc')
  const trips = await listCollection(`${vehiclePath()}/trips`, 'pageSize=12&orderBy=startedAt%20desc')
  const notifications = await listCollection(`${vehiclePath()}/notifications`, 'pageSize=20&orderBy=createdAt%20desc')
  const maintenance = await listCollection(`${vehiclePath()}/maintenance_records`, 'pageSize=20')

  const fallback = getMockDashboard()
  const charts = telemetry
    .slice()
    .reverse()
    .map((log) => ({
      time: new Date(log.recordedAt || log.timestamp || Date.now()).toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
      }),
      speed: log.speedKph || 0,
      rpm: log.rpm || 0,
      fuel: fuel.fuelPercentage || 0,
    }))

  return {
    ...fallback,
    source: 'firestore',
    car: { ...fallback.car, ...car },
    fuel: { ...fallback.fuel, ...fuel },
    liveStatus: { ...fallback.liveStatus, ...liveStatus },
    trips: trips.length ? trips : fallback.trips,
    charts: charts.length ? charts : fallback.charts,
    maintenance: maintenance.length ? maintenance : fallback.maintenance,
    notifications: notifications.length ? notifications : fallback.notifications,
    settings: {
      ...fallback.settings,
      firestoreReady: true,
      firestoreVehicleId: config.firestoreVehicleId,
    },
  }
}

export async function getFuel() {
  const dashboard = await getDashboard()
  const refuelEvents = await listCollection(`${vehiclePath()}/refuel_events`, 'pageSize=20&orderBy=createdAt%20desc')
  return {
    ...dashboard.fuel,
    refuelEvents,
    constants: dashboard.settings,
  }
}

export async function getTrips() {
  const dashboard = await getDashboard()
  return dashboard.trips
}

export async function getStatistics() {
  return getMockDashboard().statistics
}

export async function getMaintenance() {
  const dashboard = await getDashboard()
  return dashboard.maintenance
}

export async function getNotifications() {
  const dashboard = await getDashboard()
  return dashboard.notifications
}

export async function addTelemetry(payload) {
  const createdAt = payload.timestamp || new Date().toISOString()
  const telemetry = await createDocument(`${vehiclePath()}/telemetry_logs`, {
    ...payload,
    recordedAt: createdAt,
    createdAt,
  })

  await patchDocument(`${vehiclePath()}/runtime/current_status`, {
    speedKph: payload.speedKph || 0,
    rpm: payload.rpm || 0,
    engineState: payload.engineState || 'unknown',
    engineLoadPercent: payload.engineLoadPercent || 0,
    coolantTempC: payload.coolantTempC || 0,
    batteryVoltage: payload.batteryVoltage || null,
    estimatedOdometerKm: payload.estimatedOdometerKm || null,
    timestamp: createdAt,
  })

  if (payload.fuelSensorState === 'full' || payload.fuelSensorPercent >= 98) {
    await addRefuel({
      eventType: 'full_reset',
      litersAdded: 40,
      note: 'Automatic full-tank reset from Firestore telemetry full signal.',
    })
  }

  addMockTelemetry(payload)
  return telemetry
}

export async function addRefuel({ eventType, litersAdded = 0, note = '' }) {
  const createdAt = new Date().toISOString()
  const dashboard = await getDashboard()
  const tankCapacityLiters = dashboard.settings.tankCapacityLiters || 40
  const fuelAfterLiters = eventType === 'full_reset'
    ? tankCapacityLiters
    : Math.min(tankCapacityLiters, (dashboard.fuel.fuelRemainingLiters || 0) + litersAdded)

  const event = await createDocument(`${vehiclePath()}/refuel_events`, {
    eventType,
    litersAdded: eventType === 'full_reset' ? tankCapacityLiters : litersAdded,
    fuelAfterLiters,
    note,
    createdAt,
  })

  await patchDocument(`${vehiclePath()}/runtime/fuel_state`, {
    fuelRemainingLiters: fuelAfterLiters,
    fuelUsedLiters: eventType === 'full_reset' ? 0 : dashboard.fuel.fuelUsedLiters,
    fuelPercentage: Number(((fuelAfterLiters / tankCapacityLiters) * 100).toFixed(1)),
    estimatedRangeKm: Number((fuelAfterLiters / 0.1).toFixed(1)),
    lastFullResetAt: eventType === 'full_reset' ? createdAt : dashboard.fuel.lastFullResetAt,
    updatedAt: createdAt,
  })

  return event
}

export function getFirestoreStatus() {
  return {
    source: 'firestore',
    configured: isConfigured(),
    projectId: config.firebaseProjectId || null,
    vehicleId: config.firestoreVehicleId,
    requiredEnv: ['FIREBASE_PROJECT_ID', 'FIREBASE_WEB_API_KEY', 'SMARTFUEL_FIRESTORE_VEHICLE_ID'],
  }
}
