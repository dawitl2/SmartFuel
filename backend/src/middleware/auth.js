import { config } from '../config.js'

export function requireDeviceToken(req, res, next) {
  const token = req.get('x-device-token')

  if (token !== config.deviceToken) {
    return res.status(401).json({
      error: 'Unauthorized telemetry device',
      message: 'Provide a valid X-Device-Token header.',
    })
  }

  return next()
}
