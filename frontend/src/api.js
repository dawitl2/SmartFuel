const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...options,
  })

  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.message || body.error || `Request failed: ${response.status}`)
  }

  return response.json()
}

export function getDashboard() {
  return request('/api/dashboard')
}

export function createRefuelEvent(payload) {
  return request('/api/refuel', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
