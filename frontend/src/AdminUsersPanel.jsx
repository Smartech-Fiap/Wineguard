import { useEffect, useRef, useState } from 'react'
import { createAppUser, getAppUsers } from './api.js'
import LocationPicker from './LocationPicker.jsx'

const emptyLocation = { wineryName: '', city: '', region: '', country: '', latitude: '', longitude: '' }

export default function AdminUsersPanel({ csrf, onUserCreated }) {
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [reload, setReload] = useState(0)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [location, setLocation] = useState(emptyLocation)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const submission = useRef(null)

  useEffect(() => {
    const controller = new AbortController()
    getAppUsers({ signal: controller.signal }).then((data) => {
      if (!controller.signal.aborted) {
        setUsers(data)
        setListError('')
      }
    }).catch((failure) => {
      if (!controller.signal.aborted) setListError(failure.message)
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false)
    })
    return () => controller.abort()
  }, [reload])

  useEffect(() => () => submission.current?.abort(), [])

  const refresh = () => {
    setLoading(true)
    setListError('')
    setReload((current) => current + 1)
  }

  const submit = async (event) => {
    event.preventDefault()
    if (submission.current || loading) return
    setError('')
    setSuccess('')
    if (password !== confirmation) {
      setError('A confirmação deve ser igual à senha.')
      return
    }

    const controller = new AbortController()
    submission.current = controller
    setBusy(true)
    try {
      const created = await createAppUser({ username, password, location: {
        ...location,
        latitude: location.latitude === '' ? null : Number(location.latitude),
        longitude: location.longitude === '' ? null : Number(location.longitude),
      } }, csrf, { signal: controller.signal })
      if (controller.signal.aborted) return
      setUsers((current) => [...current.filter((item) => item.id !== created.id), created]
        .sort((a, b) => a.username.localeCompare(b.username)))
      setUsername('')
      setLocation(emptyLocation)
      onUserCreated?.()
      setSuccess(`Usuário “${created.username}” criado. Ele já pode entrar no painel.`)
    } catch (failure) {
      if (!controller.signal.aborted) setError(failure.message)
    } finally {
      if (!controller.signal.aborted) {
        setPassword('')
        setConfirmation('')
        setBusy(false)
      }
      if (submission.current === controller) submission.current = null
    }
  }

  return (
    <section className="panel management-panel" id="usuarios" aria-labelledby="users-title">
      <div className="panel-heading">
        <div><h2 id="users-title">Usuários <span className="heading-count">{users.length}</span></h2><p>Contas de acesso ao painel da vinheria</p></div>
        <button className="secondary-button" type="button" onClick={refresh} disabled={loading || busy}>Atualizar</button>
      </div>
      <div className="management-columns">
        <form className="management-form" onSubmit={submit}>
          <h3>Novo usuário</h3>
          <p className="management-notice">As novas contas têm perfil de usuário. Somente o administrador pode cadastrá-las.</p>
          <label htmlFor="new-user-name">Nome de usuário
            <input id="new-user-name" name="newUsername" autoComplete="off" minLength={3} maxLength={100} pattern="[A-Za-z0-9._-]+" value={username} onChange={(event) => setUsername(event.target.value)} required disabled={busy} />
          </label>
          <p className="configuration-hint">De 3 a 100 caracteres: letras sem acentos, números, ponto, hífen ou sublinhado. O login diferencia maiúsculas e minúsculas.</p>
          <label htmlFor="new-user-password">Senha inicial
            <input id="new-user-password" name="newPassword" type="password" autoComplete="new-password" minLength={12} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} required disabled={busy} />
          </label>
          <label htmlFor="new-user-confirmation">Confirmar senha
            <input id="new-user-confirmation" name="confirmPassword" type="password" autoComplete="new-password" minLength={12} maxLength={72} value={confirmation} onChange={(event) => setConfirmation(event.target.value)} required disabled={busy} />
          </label>
          <p className="configuration-hint">Pelo menos 12 caracteres. A API aceita até 72 bytes em UTF-8.</p>
          <h3>Localização da vinícola</h3>
          <p className="management-notice">O nome e a localização da vinícola serão visíveis a todas as contas autenticadas. Os sensores continuam privados.</p>
          {Object.entries({ wineryName: 'Nome da vinícola', city: 'Cidade', region: 'Estado / região', country: 'País' }).map(([field, label]) => (
            <label key={field} htmlFor={`location-${field}`}>{label}
              <input id={`location-${field}`} value={location[field]} maxLength={field === 'wineryName' ? 150 : 100} required disabled={busy} onChange={(event) => setLocation((current) => ({ ...current, [field]: event.target.value }))} />
            </label>
          ))}
          <p className="configuration-hint">Aproxime o mapa e clique no local da vinícola, ou digite as coordenadas abaixo. O mapa não preenche os nomes dos lugares automaticamente.</p>
          <LocationPicker latitude={location.latitude} longitude={location.longitude} disabled={busy} onSelect={(point) => setLocation((current) => ({ ...current, ...point }))} />
          {['latitude', 'longitude'].map((field) => <label key={field} htmlFor={`location-${field}`}>{field === 'latitude' ? 'Latitude' : 'Longitude'}
            <input id={`location-${field}`} type="number" step="0.000001" min={field === 'latitude' ? -90 : -180} max={field === 'latitude' ? 90 : 180} value={location[field]} required disabled={busy} onChange={(event) => setLocation((current) => ({ ...current, [field]: event.target.value }))} />
          </label>)}
          {error && <p className="form-error" role="alert">{error}</p>}
          {success && <p className="form-success" role="status">{success}</p>}
          <button className="primary-button" type="submit" disabled={loading || busy}>{busy ? 'Cadastrando…' : 'Cadastrar usuário'}</button>
        </form>
        <div className="management-list" aria-busy={loading}>
          <h3>Contas cadastradas</h3>
          {loading && <p role="status">Carregando usuários…</p>}
          {listError && <p className="form-error" role="alert">{listError}</p>}
          {!loading && !listError && users.length === 0 && <p>Nenhum usuário cadastrado.</p>}
          {!loading && !listError && users.map((account) => (
            <article className="management-item" key={account.id}>
              <span className="management-item-icon" aria-hidden="true">{account.username.charAt(0).toUpperCase()}</span>
              <div><strong>{account.username}</strong><p>{account.enabled ? 'Conta habilitada' : 'Conta desabilitada'}</p><p>{account.location ? `${account.location.wineryName} · ${account.location.city}, ${account.location.country}` : account.role === 'ADMIN' ? 'Administrador sem localização cadastrada' : 'Localização ainda não cadastrada'}</p></div>
              <span className="management-id">{account.role === 'ADMIN' ? 'Administrador' : 'Usuário'}</span>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}
