import { test } from 'node:test'
import assert from 'node:assert/strict'
import { inspectReading, recordReading, emptyNoticeLog, createNoticeStore } from './notices.js'

const now = Date.parse('2026-10-30T12:00:00Z')
const cfg = { state: 'APPLIED', revision: 2, temperatureMin: 15, temperatureMax: 29, humidityMin: 40, humidityMax: 82, luminosityMax: 3500 }
const reading = { measuredAt: new Date(now).toISOString(), temperature: 30, humidity: 60, luminosity: 2000 }
const inspect = (data = reading, config = cfg) => inspectReading(data, config, now)

test('registra uma ocorrência por anomalia e não repete a mesma amostra', () => {
  const log = recordReading(emptyNoticeLog(), inspect())
  assert.equal(log.events.length, 1)
  assert.equal(log.events[0].metric, 'temperature')
  assert.equal(recordReading(log, inspect()), log)
  const next = recordReading(log, inspect({ ...reading, temperature: 32, measuredAt: new Date(now + 10000).toISOString() }))
  assert.equal(next.events.length, 1)
  assert.equal(next.events[0].firstValue, 30)
  assert.equal(next.events[0].value, 32)
  assert.equal(log.events[0].value, 30)
})

test('normalização encerra ocorrência e nova anomalia cria outra', () => {
  const log = recordReading(emptyNoticeLog(), inspect())
  const normal = recordReading(log, inspect({ ...reading, temperature: 29, measuredAt: new Date(now + 1000).toISOString() }))
  assert.equal(normal.events[0].closedReason, 'normal')
  const again = recordReading(normal, inspect({ ...reading, measuredAt: new Date(now + 2000).toISOString() }))
  assert.equal(again.events.length, 2)
  assert.equal(again.events.filter((event) => !event.closedAt).length, 1)
})

test('não avalia limites pendentes, amostra antiga ou horário no futuro', () => {
  for (const state of ['PENDING', 'UNCONFIRMED', 'REJECTED']) assert.equal(inspect(reading, { ...cfg, state }).checks.length, 0)
  assert.equal(inspectReading(reading, cfg, now + 120001).checks.length, 0)
  assert.equal(inspectReading(reading, cfg, now - 30001).checks.length, 0)
  assert.equal(inspect({ ...reading, measuredAt: null }).checks.length, 0)
  assert.equal(inspect(reading, { ...cfg, createdAt: new Date(now + 1000).toISOString() }).checks.length, 0)
})

test('valor ausente não encerra aviso, luminosidade zero é uma leitura válida', () => {
  const log = recordReading(emptyNoticeLog(), inspect())
  const partial = inspect({ ...reading, temperature: null, luminosity: 0, measuredAt: new Date(now + 1000).toISOString() })
  assert.equal(partial.reason, 'partial')
  assert.equal(partial.checks.find((check) => check.metric === 'luminosity').outside, false)
  assert.equal(recordReading(log, partial).events[0].closedAt, null)
})

test('troca de revisão distingue alteração dos limites de normalização', () => {
  const log = recordReading(emptyNoticeLog(), inspect())
  const result = recordReading(log, inspect({ ...reading, measuredAt: new Date(now + 1000).toISOString() }, { ...cfg, revision: 3 }))
  assert.equal(result.events.length, 2)
  assert.equal(result.events.find((event) => event.revision === 2).closedReason, 'limits_changed')
})

test('detecta simultaneamente as três métricas e preserva igualdade com limites', () => {
  assert.equal(recordReading(emptyNoticeLog(), inspect({ ...reading, humidity: 39, luminosity: 3501 })).events.length, 3)
  assert.equal(recordReading(emptyNoticeLog(), inspect({ ...reading, temperature: 15, humidity: 82, luminosity: 3500 })).events.length, 0)
})

test('persiste ao recriar a tela e separa usuário e dispositivo', () => {
  const values = new Map()
  const storage = { getItem: (key) => values.get(key), setItem: (key, value) => values.set(key, value) }
  const store = createNoticeStore('Artur', 1, storage)
  store.observe(inspect())
  assert.equal(createNoticeStore('Artur', 1, storage).getSnapshot().events.length, 1)
  assert.equal(createNoticeStore('outro', 1, storage).getSnapshot().events.length, 0)
  assert.equal(createNoticeStore('Artur', 2, storage).getSnapshot().events.length, 0)
})

test('falha de armazenamento mantém avisos na sessão e informa o problema', () => {
  const storage = { getItem: () => { throw Error('bloqueado') }, setItem: () => { throw Error('cheio') } }
  const store = createNoticeStore('Artur', 1, storage)
  store.observe(inspect())
  assert.equal(store.getSnapshot().events.length, 1)
  assert.equal(store.getSnapshot().storageError, true)
})

test('limita o histórico e ignora leitura fora de ordem', () => {
  let log = emptyNoticeLog()
  for (let index = 0; index < 410; index++) {
    const at = now + index * 1000
    log = recordReading(log, inspectReading({ ...reading, temperature: index % 2 ? 20 : 30, measuredAt: new Date(at).toISOString() }, cfg, at))
  }
  assert.equal(log.events.length, 200)
  assert.equal(recordReading(log, inspect()), log)
})
