import { Router } from 'express'
import { z } from 'zod'
import { requireDeviceToken } from './middleware/auth.js'
import { getDataSourceStatus, getStore } from './data/store.js'

const router = Router()

const telemetrySchema = z.object({
  speedKph: z.number().min(0).max(260),
  rpm: z.number().min(0).max(9000).optional(),
  distanceIncrementKm: z.number().min(0).max(20).optional(),
  sampleSeconds: z.number().min(1).max(600).optional(),
  estimatedOdometerKm: z.number().min(0).optional(),
  engineLoadPercent: z.number().min(0).max(100).optional(),
  coolantTempC: z.number().min(-40).max(150).optional(),
  batteryVoltage: z.number().min(0).max(30).optional(),
  latitude: z.number().min(-90).max(90).optional(),
  longitude: z.number().min(-180).max(180).optional(),
  timestamp: z.string().datetime().optional(),
  tripId: z.string().optional(),
  engineState: z.enum(['off', 'idle', 'driving', 'unknown']).optional(),
  fuelSensorPercent: z.number().min(0).max(100).optional(),
  fuelSensorState: z.enum(['unknown', 'full', 'stuck', 'descending']).optional(),
  obd: z.record(z.any()).optional(),
})

const refuelSchema = z.object({
  eventType: z.enum(['full_reset', 'partial_refuel']),
  litersAdded: z.number().min(0).max(40).optional(),
  note: z.string().max(240).optional(),
})

function validate(schema) {
  return (req, res, next) => {
    const result = schema.safeParse(req.body)

    if (!result.success) {
      return res.status(400).json({
        error: 'Validation failed',
        issues: result.error.flatten(),
      })
    }

    req.body = result.data
    return next()
  }
}

function asyncRoute(handler) {
  return async (req, res, next) => {
    try {
      await handler(req, res)
    } catch (error) {
      next(error)
    }
  }
}

router.get('/data-source', (_req, res) => {
  res.json(getDataSourceStatus())
})

router.get('/dashboard', asyncRoute(async (req, res) => {
  const store = getStore(req.query.source)
  res.json(await store.getDashboard())
}))

router.get('/fuel', asyncRoute(async (req, res) => {
  const store = getStore(req.query.source)
  res.json(await store.getFuel())
}))

router.get('/trips', asyncRoute(async (req, res) => {
  const store = getStore(req.query.source)
  res.json({ trips: await store.getTrips() })
}))

router.get('/statistics', asyncRoute(async (req, res) => {
  const store = getStore(req.query.source)
  res.json(await store.getStatistics())
}))

router.get('/maintenance', asyncRoute(async (req, res) => {
  const store = getStore(req.query.source)
  res.json({ maintenance: await store.getMaintenance() })
}))

router.get('/notifications', asyncRoute(async (req, res) => {
  const store = getStore(req.query.source)
  res.json({ notifications: await store.getNotifications() })
}))

router.post('/telemetry', requireDeviceToken, validate(telemetrySchema), asyncRoute(async (req, res) => {
  const store = getStore(req.query.source)
  const log = await store.addTelemetry(req.body)
  res.status(201).json({
    message: 'Telemetry accepted',
    source: req.query.source || 'mock',
    telemetry: log,
    fuel: await store.getFuel(),
  })
}))

router.post('/refuel', validate(refuelSchema), asyncRoute(async (req, res) => {
  const store = getStore(req.query.source)
  const event = await store.addRefuel(req.body)
  res.status(201).json({
    message: 'Refuel event recorded',
    source: req.query.source || 'mock',
    event,
    fuel: await store.getFuel(),
  })
}))

export default router
