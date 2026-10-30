const MAX_AGE_MS = 120000
const MAX_EVENTS = 200

// Apenas leituras recentes e limites confirmados geram avisos visuais.
// Estes avisos não enviam comandos e não substituem os alertas do firmware.
export function inspectReading(readings, configuration, now = Date.now()) {
  if (configuration?.state !== 'APPLIED') return { reason: 'limits', checks: [] }
  const timestamp = Date.parse(readings?.measuredAt)
  if (timestamp < Date.parse(configuration.createdAt)) return { reason: 'limits', checks: [] }
  if (!Number.isFinite(timestamp) || now - timestamp > MAX_AGE_MS || timestamp > now + 30000) {
    return { reason: 'stale', checks: [] }
  }
  const definitions = [
    ['temperature', configuration.temperatureMin, configuration.temperatureMax],
    ['humidity', configuration.humidityMin, configuration.humidityMax],
    ['luminosity', null, configuration.luminosityMax],
  ]
  const checks = definitions.flatMap(([metric, minimum, maximum]) => {
    const value = readings?.[metric]
    if (!Number.isFinite(value) || !Number.isFinite(maximum) || (minimum !== null && !Number.isFinite(minimum))) return []
    return [{ metric, value, minimum, maximum, outside: value > maximum || (minimum !== null && value < minimum) }]
  })
  return { reason: checks.length === 3 ? null : 'partial', timestamp, revision: configuration.revision, checks }
}

export const emptyNoticeLog = () => ({ events: [], lastSampleAt: null, storageError: false })

export function recordReading(log, inspection) {
  if (!inspection.checks.length || inspection.timestamp <= (log.lastSampleAt ?? -Infinity)) return log
  const events = log.events.map((event) => ({ ...event }))
  const at = new Date(inspection.timestamp).toISOString()
  for (const check of inspection.checks) {
    let active = events.find((event) => event.metric === check.metric && !event.closedAt)
    if (active && active.revision !== inspection.revision) {
      active.closedAt = at
      active.closedReason = 'limits_changed'
      active = null
    }
    if (check.outside) {
      if (active) {
        active.value = check.value
        active.lastSeenAt = at
      } else {
        events.unshift({ id: `${inspection.revision}-${check.metric}-${at}`, metric: check.metric,
          revision: inspection.revision, minimum: check.minimum, maximum: check.maximum,
          firstValue: check.value, value: check.value, openedAt: at, lastSeenAt: at, closedAt: null })
      }
    } else if (active) {
      active.closedAt = at
      active.closedReason = 'normal'
      active.value = check.value
      active.lastSeenAt = at
    }
  }
  // Avisos ainda abertos têm prioridade; os encerrados mais antigos saem primeiro.
  events.sort((a, b) => Number(Boolean(a.closedAt)) - Number(Boolean(b.closedAt)) || Date.parse(b.openedAt) - Date.parse(a.openedAt))
  return { ...log, events: events.slice(0, MAX_EVENTS), lastSampleAt: inspection.timestamp }
}

function readLog(storage, key) {
  const raw = storage.getItem(key)
  if (!raw) return emptyNoticeLog()
  const parsed = JSON.parse(raw)
  if (!Array.isArray(parsed.events) || parsed.events.length > MAX_EVENTS ||
      !(parsed.lastSampleAt === null || Number.isFinite(parsed.lastSampleAt)) ||
      !parsed.events.every((event) => typeof event.id === 'string' &&
        ['temperature', 'humidity', 'luminosity'].includes(event.metric) &&
        Number.isFinite(event.revision) && Number.isFinite(event.value) && Number.isFinite(event.firstValue) &&
        Number.isFinite(event.maximum) && (event.minimum === null || Number.isFinite(event.minimum)) &&
        Number.isFinite(Date.parse(event.openedAt)) && Number.isFinite(Date.parse(event.lastSeenAt)) &&
        (event.closedAt === null || Number.isFinite(Date.parse(event.closedAt))))) return emptyNoticeLog()
  return { events: parsed.events, lastSampleAt: parsed.lastSampleAt, storageError: false }
}

export function createNoticeStore(username, deviceId, storage) {
  const key = `wineguard:notices:v1:${encodeURIComponent(username)}:${deviceId}`
  let snapshot
  try {
    storage ??= window.localStorage
    snapshot = readLog(storage, key)
  } catch { snapshot = { ...emptyNoticeLog(), storageError: true } }
  const listeners = new Set()
  return {
    getSnapshot: () => snapshot,
    subscribe: (listener) => { listeners.add(listener); return () => listeners.delete(listener) },
    observe: (inspection) => {
      const next = recordReading(snapshot, inspection)
      if (next === snapshot) return
      snapshot = next
      try {
        storage.setItem(key, JSON.stringify({ events: next.events, lastSampleAt: next.lastSampleAt }))
        snapshot = { ...snapshot, storageError: false }
      } catch { snapshot = { ...snapshot, storageError: true } }
      listeners.forEach((listener) => listener())
    },
  }
}
