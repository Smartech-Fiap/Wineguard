import { useEffect, useMemo, useState, useSyncExternalStore } from 'react'
import { createNoticeStore, inspectReading } from './notices.js'
import { luminosityPercent } from './luminosity.js'

const labels = { temperature: 'Temperatura', humidity: 'Umidade', luminosity: 'Luminosidade' }
const valueLabel = (metric, value) => `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 }).format(metric === 'luminosity' ? luminosityPercent(value) : value)} ${metric === 'temperature' ? '°C' : '%'}`
const dateLabel = (value) => new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(value))
const rangeLabel = (entry) => entry.minimum === null ? `Até ${valueLabel(entry.metric, entry.maximum)}` : `${valueLabel(entry.metric, entry.minimum)} a ${valueLabel(entry.metric, entry.maximum)}`

export function NoticeBanner({ readings, configuration, readingsError }) {
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 10000)
    return () => clearInterval(timer)
  }, [])
  const outside = readingsError ? [] : inspectReading(readings, configuration, now).checks.filter((check) => check.outside)
  if (!outside.length) return null
  return <div className="environment-notice" role="alert"><strong>Atenção: {outside.map((entry) => `${labels[entry.metric]} ${valueLabel(entry.metric, entry.value)}`).join(' · ')} fora dos limites.</strong><a href="#avisos">Consultar os avisos e o histórico</a></div>
}

export default function NoticesPanel({ username, deviceId, readings, configuration, readingsError }) {
  const store = useMemo(() => createNoticeStore(username, deviceId), [username, deviceId])
  const log = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 10000)
    return () => clearInterval(timer)
  }, [])
  const inspection = useMemo(() => inspectReading(readings, configuration, now), [readings, configuration, now])
  useEffect(() => { if (!readingsError) store.observe(inspection) }, [store, inspection, readingsError])
  const outside = readingsError ? [] : inspection.checks.filter((check) => check.outside)
  return <section className="panel management-panel" id="avisos">
    <div className="panel-heading"><div><h2>Avisos ambientais</h2><p>Dispositivo selecionado · comparação com os limites confirmados no ESP</p></div><span className="coming-soon">{readingsError || inspection.reason ? 'Avaliação incompleta' : `${outside.length} fora da faixa`}</span></div>
    <p className="history-help">Histórico salvo apenas neste navegador, separado por usuário e dispositivo. Registra até 200 ocorrências observadas com o painel aberto. Não recupera avisos de períodos em que o painel estava fechado e não aciona o ESP.</p>
    {log.storageError && <p className="form-error" role="alert">Não foi possível salvar no navegador. Os avisos desta sessão podem ser perdidos ao fechar a página.</p>}
    {(readingsError || inspection.reason) && <p className="management-notice" role="status">{readingsError ? 'Sem consulta recente bem-sucedida; não é possível confirmar o estado atual.' : inspection.reason === 'limits' ? 'Aguardando uma configuração confirmada para avaliar os limites. Configurações pendentes não geram novos avisos.' : inspection.reason === 'stale' ? 'A leitura está desatualizada ou sem horário válido. Não é possível confirmar normalização.' : 'Uma ou mais medições estão indisponíveis. Apenas os valores válidos estão sendo avaliados.'}</p>}
    {outside.map((entry) => <div className="environment-notice" role="alert" key={entry.metric}><strong>{labels[entry.metric]} fora do limite: {valueLabel(entry.metric, entry.value)}</strong><span>Faixa: {rangeLabel(entry)} · leitura de {dateLabel(readings.measuredAt)}</span></div>)}
    {!readingsError && !inspection.reason && !outside.length && <p className="form-success" role="status">Os três valores da leitura recente estão dentro dos limites confirmados.</p>}
    <details className="history-table-details"><summary>Histórico de avisos ({log.events.length})</summary>
      {!log.events.length ? <p>Nenhum aviso registrado neste navegador para este usuário e dispositivo.</p> : <div className="history-table-scroll"><table><caption>Ocorrências observadas neste navegador. Avisos sem encerramento precisam de nova leitura válida para confirmar o retorno à faixa.</caption><thead><tr><th>Parâmetro</th><th>Início</th><th>Valor inicial</th><th>Último valor observado</th><th>Faixa</th><th>Encerramento</th></tr></thead><tbody>{log.events.map((entry) => <tr key={entry.id}><td>{labels[entry.metric]} · rev. {entry.revision}</td><td>{dateLabel(entry.openedAt)}</td><td>{valueLabel(entry.metric, entry.firstValue)}</td><td>{valueLabel(entry.metric, entry.value)}<br /><small>{dateLabel(entry.lastSeenAt)}</small></td><td>{rangeLabel(entry)}</td><td>{entry.closedAt ? <>{entry.closedReason === 'normal' ? 'Retornou à faixa' : 'Limites alterados'}<br />{dateLabel(entry.closedAt)}</> : 'Sem encerramento confirmado'}</td></tr>)}</tbody></table></div>}
    </details>
  </section>
}
