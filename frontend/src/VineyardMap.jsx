import { useEffect, useRef, useState } from 'react'
import { MapContainer, Marker, TileLayer, useMap } from 'react-leaflet'
import L from 'leaflet'
import { getWineries } from './api.js'
import 'leaflet/dist/leaflet.css'

const markerIcon = L.divIcon({
  className: 'vineyard-marker',
  html: '<span aria-hidden="true">📍</span>',
  iconSize: [34, 40],
  iconAnchor: [17, 38],
})

function FitProperties({ properties }) {
  const map = useMap()

  useEffect(() => {
    if (properties.length) {
      const bounds = L.latLngBounds(properties.map((property) => property.position))
      map.fitBounds(bounds, { padding: [28, 28], maxZoom: 10 })
    } else {
      map.setView([18, 8], 1)
    }
  }, [map, properties])

  return null
}

export default function VineyardMap({ onOpenDetails, refreshKey }) {
  const [properties, setProperties] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getWineries({ signal: controller.signal }).then((points) => {
      if (controller.signal.aborted) return
      setProperties(points.map((point) => ({
        id: point.id, name: point.wineryName, city: point.city,
        region: point.region, country: point.country,
        position: [point.latitude, point.longitude],
      })))
      setError('')
    }).catch((failure) => {
      if (!controller.signal.aborted) {
        setProperties([])
        setError(failure.message)
      }
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false)
    })
    return () => controller.abort()
  }, [refreshKey, reload])

  const [hoveredId, setHoveredId] = useState(null)
  const [pinnedId, setPinnedId] = useState(null)
  const visibleId = pinnedId || hoveredId
  const visibleProperty = properties.find((property) => property.id === visibleId)

  return (
    <section className="panel vineyard-map-panel" aria-labelledby="vineyard-map-title">
      <div className="panel-heading vineyard-map-heading">
        <div>
          <h2 id="vineyard-map-title">Vinícolas pelo mundo</h2>
          <p>Vinícolas cadastradas · localização compartilhada, sensores privados</p>
        </div>
        <button className="secondary-button" type="button" disabled={loading} onClick={() => { setLoading(true); setError(''); setReload((value) => value + 1) }}>Atualizar mapa</button>
      </div>
      {loading && <p role="status">Carregando localizações…</p>}
      {error && <p className="form-error" role="alert">Não foi possível carregar o mapa: {error}</p>}
      {!loading && !error && properties.length === 0 && <p>Nenhuma vinícola com localização cadastrada.</p>}
      <div className="vineyard-map-layout">
        <div className="vineyard-map">
          <MapContainer
            center={[18, 8]}
            zoom={1}
            minZoom={0}
            maxZoom={12}
            scrollWheelZoom={false}
            worldCopyJump
            aria-label="Mapa global de propriedades vitivinícolas"
          >
            <FitProperties properties={properties} />
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            {properties.map((property) => (
              <Marker
                key={property.id}
                position={property.position}
                icon={markerIcon}
                title={`${property.name}, ${property.country}`}
                eventHandlers={{
                  mouseover: () => setHoveredId(property.id),
                  mouseout: () => setHoveredId(null),
                  click: () => setPinnedId(property.id),
                }}
              />
            ))}
          </MapContainer>
          {visibleProperty && (
            <article className={`map-property-preview ${pinnedId === visibleProperty.id ? 'pinned-preview' : 'hover-preview'}`} aria-live="polite">
              <button className="map-preview-close" onClick={() => setPinnedId(null)} aria-label="Fechar resumo">×</button>
              <span className="map-preview-eyebrow">VINÍCOLA CADASTRADA</span>
              <h3>{visibleProperty.name}</h3>
              <p>{visibleProperty.city} · {visibleProperty.region} · {visibleProperty.country}</p>
              <span className="map-preview-pending">Localização informada pelo administrador</span>
              <button className="map-details-button" onClick={(event) => onOpenDetails(visibleProperty, event.currentTarget)}>Ver detalhes da vinícola <span aria-hidden="true">→</span></button>
            </article>
          )}
        </div>
        <aside className="map-property-list" aria-label="Vinícolas cadastradas">
          <div className="map-list-heading"><strong>Vinícolas no mapa</strong><span>{properties.length}</span></div>
          {properties.map((property) => (
            <button
              className={`map-property-item ${pinnedId === property.id ? 'selected' : ''}`}
              key={property.id}
              onMouseEnter={() => setHoveredId(property.id)}
              onMouseLeave={() => setHoveredId(null)}
              onFocus={() => setHoveredId(property.id)}
              onBlur={() => setHoveredId(null)}
              onClick={() => setPinnedId(property.id)}
            >
              <span className="map-list-pin" aria-hidden="true">📍</span>
              <span><strong>{property.name}</strong><small>{property.city} · {property.country}</small></span>
            </button>
          ))}
          <p className="map-list-note">O mapa compartilha apenas nome e localização. Leituras e dispositivos não são exibidos.</p>
        </aside>
      </div>
    </section>
  )
}

export function VineyardDetails({ property, onClose }) {
  const dialogRef = useRef(null)
  const closeButtonRef = useRef(null)

  useEffect(() => {
    if (!property) return undefined
    closeButtonRef.current?.focus()
    return undefined
  }, [property])

  if (!property) return null

  const trapDialogFocus = (event) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      onClose()
      return
    }
    if (event.key !== 'Tab') return

    const focusableElements = dialogRef.current?.querySelectorAll('button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])')
    if (!focusableElements?.length) {
      event.preventDefault()
      dialogRef.current?.focus()
      return
    }

    const first = focusableElements[0]
    const last = focusableElements[focusableElements.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return (
    <div className="vineyard-modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section className="vineyard-modal" ref={dialogRef} onKeyDown={trapDialogFocus} role="dialog" aria-modal="true" aria-labelledby="vineyard-modal-title" tabIndex="-1">
        <button className="vineyard-modal-close" ref={closeButtonRef} onClick={onClose} aria-label="Fechar detalhes">×</button>
        <span className="map-preview-eyebrow">DETALHES · LOCALIZAÇÃO CADASTRADA</span>
        <h2 id="vineyard-modal-title">{property.name}</h2>
        <p className="vineyard-modal-region">{property.city} · {property.region} · {property.country}</p>
        <div className="vineyard-coordinates"><span>Latitude <strong>{property.position[0].toFixed(4)}°</strong></span><span>Longitude <strong>{property.position[1].toFixed(4)}°</strong></span></div>
        <p className="vineyard-modal-note">Localização informada no cadastro da conta. Os dados dos sensores permanecem restritos ao proprietário e ao administrador.</p>
        <button className="map-details-button" onClick={onClose}>Voltar ao mapa</button>
      </section>
    </div>
  )
}
