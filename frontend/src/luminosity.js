// Percentual relativo da escala ADC de 12 bits; não representa lux calibrados.
export function luminosityPercent(value) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return null
  return Math.min(100, Math.max(0, value / 4095 * 100))
}

export function luminosityAdc(percent) {
  if (typeof percent !== 'number' || !Number.isFinite(percent) || percent < 5 || percent > 100) {
    throw new Error('Informe luminosidade máxima entre 5 e 100%.')
  }
  return Math.round(percent / 100 * 4095)
}
