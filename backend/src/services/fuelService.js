export const FUEL_CONSTANTS = {
  tankCapacityLiters: 40,
  maxRangeKm: 400,
  consumptionLitersPerKm: 0.1,
}

export function drivingIntensityMultiplier({ speedKph = 0, rpm = 0, engineLoadPercent = 0 } = {}) {
  if (speedKph <= 1 && rpm > 500) return 1.25

  const rpmPenalty = rpm > 4200 ? 0.45 : rpm > 3200 ? 0.25 : rpm > 2500 ? 0.12 : 0
  const loadPenalty = engineLoadPercent > 80 ? 0.25 : engineLoadPercent > 60 ? 0.12 : 0
  const cityPenalty = speedKph > 0 && speedKph < 25 ? 0.12 : 0

  return Number((1 + rpmPenalty + loadPenalty + cityPenalty).toFixed(2))
}

export function calculateFuelUsedForSample({ distanceIncrementKm = 0, speedKph = 0, rpm = 0, engineLoadPercent = 0 } = {}) {
  const multiplier = drivingIntensityMultiplier({ speedKph, rpm, engineLoadPercent })
  return Number((distanceIncrementKm * FUEL_CONSTANTS.consumptionLitersPerKm * multiplier).toFixed(4))
}

export function calculateFuelState(distanceKm, litersAdded = 0, drivingAdjustmentLiters = 0) {
  const fuelUsedLiters = Number((distanceKm * FUEL_CONSTANTS.consumptionLitersPerKm + drivingAdjustmentLiters).toFixed(2))
  const fuelRemainingLiters = Math.min(
    FUEL_CONSTANTS.tankCapacityLiters,
    Math.max(0, FUEL_CONSTANTS.tankCapacityLiters - fuelUsedLiters + litersAdded),
  )
  const estimatedRangeKm = Number((fuelRemainingLiters / FUEL_CONSTANTS.consumptionLitersPerKm).toFixed(1))
  const fuelPercentage = Number(((fuelRemainingLiters / FUEL_CONSTANTS.tankCapacityLiters) * 100).toFixed(1))

  return {
    tankCapacityLiters: FUEL_CONSTANTS.tankCapacityLiters,
    consumptionLitersPerKm: FUEL_CONSTANTS.consumptionLitersPerKm,
    fuelUsedLiters,
    fuelRemainingLiters: Number(fuelRemainingLiters.toFixed(2)),
    estimatedRangeKm,
    fuelPercentage,
  }
}

export function deriveEngineState({ speedKph = 0, rpm = 0 } = {}) {
  if (rpm <= 100) return 'off'
  if (speedKph <= 1) return 'idle'
  return 'driving'
}

export function applyRefuel(currentFuelLiters, litersAdded, eventType) {
  const fuelAfterLiters =
    eventType === 'full_reset'
      ? FUEL_CONSTANTS.tankCapacityLiters
      : Math.min(FUEL_CONSTANTS.tankCapacityLiters, currentFuelLiters + litersAdded)

  return {
    fuelAfterLiters: Number(fuelAfterLiters.toFixed(2)),
    estimatedRangeKm: Number((fuelAfterLiters / FUEL_CONSTANTS.consumptionLitersPerKm).toFixed(1)),
    fuelPercentage: Number(((fuelAfterLiters / FUEL_CONSTANTS.tankCapacityLiters) * 100).toFixed(1)),
  }
}
