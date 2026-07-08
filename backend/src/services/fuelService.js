export const FUEL_CONSTANTS = {
  tankCapacityLiters: 40,
  maxRangeKm: 400,
  consumptionLitersPerKm: 0.1,
}

export function calculateFuelState(distanceKm, litersAdded = 0) {
  const fuelUsedLiters = Number((distanceKm * FUEL_CONSTANTS.consumptionLitersPerKm).toFixed(2))
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
