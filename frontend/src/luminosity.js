// Percentual relativo da escala ADC de 12 bits; não representa lux calibrados.
export function luminosityPercent(value) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return null
  return Math.min(100, Math.max(0, value / 4095 * 100))
}
