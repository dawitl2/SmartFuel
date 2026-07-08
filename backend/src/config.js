import dotenv from 'dotenv'

dotenv.config()

export const config = {
  port: Number(process.env.PORT || 4000),
  webOrigin: process.env.WEB_ORIGIN || 'http://localhost:5173',
  deviceToken: process.env.DEVICE_TOKEN || 'smartfuel-demo-device-token',
}
