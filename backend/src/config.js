import dotenv from 'dotenv'

dotenv.config()

export const config = {
  port: Number(process.env.PORT || 4000),
  webOrigin: process.env.WEB_ORIGIN || 'http://localhost:5173',
  deviceToken: process.env.DEVICE_TOKEN || 'smartfuel-demo-device-token',
  dataSource: process.env.SMARTFUEL_DATA_SOURCE || 'mock',
  firebaseProjectId: process.env.FIREBASE_PROJECT_ID || '',
  firebaseWebApiKey: process.env.FIREBASE_WEB_API_KEY || '',
  firestoreVehicleId: process.env.SMARTFUEL_FIRESTORE_VEHICLE_ID || 'demo-vehicle',
}
