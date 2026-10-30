import { test } from 'node:test'
import assert from 'node:assert/strict'
import { luminosityPercent, luminosityAdc } from './luminosity.js'

test('converte a escala ADC em percentual sem perder o zero', () => {
  assert.equal(luminosityPercent(0), 0)
  assert.equal(luminosityPercent(4095), 100)
  assert.equal(luminosityPercent(2047.5), 50)
  assert.ok(Math.abs(luminosityPercent(3500) - 85.47008547) < 0.000001)
})

test('mantém valores indisponíveis e limita a apresentação a 0–100%', () => {
  for (const value of [null, undefined, NaN, Infinity, '3500']) {
    assert.equal(luminosityPercent(value), null)
  }
  assert.equal(luminosityPercent(-1), 0)
  assert.equal(luminosityPercent(5000), 100)
})

test('converte o limite percentual para o inteiro aceito pelo ESP', () => {
  assert.equal(luminosityAdc(100), 4095)
  assert.equal(luminosityAdc(5), 205)
  assert.equal(luminosityAdc(85.5), 3501)
  for (const value of [null, '', NaN, 4.8, 100.1]) assert.throws(() => luminosityAdc(value))
  assert.equal(luminosityAdc(luminosityPercent(3500)), 3500)
})
