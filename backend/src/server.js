import cors from 'cors'
import express from 'express'
import helmet from 'helmet'
import morgan from 'morgan'
import { config } from './config.js'
import routes from './routes.js'

const app = express()

app.use(helmet())
app.use(
  cors({
    origin: config.webOrigin,
  }),
)
app.use(express.json({ limit: '128kb' }))
app.use(morgan('dev'))

app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    service: 'smartfuel-backend',
    timestamp: new Date().toISOString(),
  })
})

app.use('/api', routes)

app.use((req, res) => {
  res.status(404).json({
    error: 'Not found',
    path: req.originalUrl,
  })
})

app.use((err, _req, res, _next) => {
  console.error(err)
  res.status(500).json({
    error: 'Internal server error',
  })
})

app.listen(config.port, () => {
  console.log(`SmartFuel backend running at http://localhost:${config.port}`)
})
