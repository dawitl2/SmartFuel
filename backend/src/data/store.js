import { config } from '../config.js'
import * as mockStore from './demoStore.js'
import * as firestoreStore from './firestoreRestStore.js'

export function resolveSource(requestedSource) {
  if (requestedSource === 'firestore' || requestedSource === 'mock') return requestedSource
  if (config.dataSource === 'firestore') return 'firestore'
  return 'mock'
}

export function getStore(requestedSource) {
  const source = resolveSource(requestedSource)
  return source === 'firestore' ? firestoreStore : mockStore
}

export function getDataSourceStatus() {
  return {
    defaultSource: resolveSource(),
    sources: {
      mock: {
        configured: true,
        description: 'Built-in deterministic SmartFuel demo data.',
      },
      firestore: firestoreStore.getFirestoreStatus(),
    },
  }
}
