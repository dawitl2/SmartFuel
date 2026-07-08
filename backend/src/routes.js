import { Router } from 'express'
import { z } from 'zod'
import { requireDeviceToken } from './middleware/auth.js'
import {
  addRefuel,
  addTelemetry,
  getDashboard,
  getFuel,
  getMaintenance,
  getNotifications,
  getStatistics,
  getTrips,
} from './data/demoStore.js'

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

router.get('/dashboard', (_req, res) => {
  res.json(getDashboard())
})

router.get('/fuel', (_req, res) => {
  res.json(getFuel())
})

router.get('/trips', (_req, res) => {
  res.json({ trips: getTrips() })
})

router.get('/statistics', (_req, res) => {
  res.json(getStatistics())
})

router.get('/maintenance', (_req, res) => {
  res.json({ maintenance: getMaintenance() })
})

router.get('/notifications', (_req, res) => {
  res.json({ notifications: getNotifications() })
})

router.post('/telemetry', requireDeviceToken, validate(telemetrySchema), (req, res) => {
  const log = addTelemetry(req.body)
  res.status(201).json({
    message: 'Telemetry accepted',
    telemetry: log,
    fuel: getFuel(),
  })
})

router.post('/refuel', validate(refuelSchema), (req, res) => {
  const event = addRefuel(req.body)
  res.status(201).json({
    message: 'Refuel event recorded',
    event,
    fuel: getFuel(),
  })
})

export default router
