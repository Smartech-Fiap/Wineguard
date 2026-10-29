import { useEffect } from 'react'
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const pin = L.divIcon({
  className: 'vineyard-marker', html: '<span aria-hidden="true">📍</span>',
  iconSize: [34, 40], iconAnchor: [17, 38],
})

function Selection({ position, onSelect, disabled }) {
  const map = useMap()
  useMapEvents({
    click(event) {
      if (!disabled) onSelect({
        latitude: Number(event.latlng.lat.toFixed(6)),
        longitude: Number(event.latlng.wrap().lng.toFixed(6)),
      })
    },
  })
  const latitude = position?.[0]
  const longitude = position?.[1]
  useEffect(() => {
    if (latitude !== undefined && longitude !== undefined) {
      map.panTo([latitude, longitude])
    }
  }, [map, latitude, longitude])
  return position ? <Marker position={position} icon={pin} /> : null
}

export default function LocationPicker({ latitude, longitude, onSelect, disabled }) {
  const lat = latitude === '' ? NaN : Number(latitude)
  const lng = longitude === '' ? NaN : Number(longitude)
  const position = Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180
    ? [lat, lng] : null
  return <div className="location-picker">
    <MapContainer center={[10, 0]} zoom={1} minZoom={1} maxZoom={18} scrollWheelZoom={false} aria-label="Escolher localização da vinícola; coordenadas também podem ser digitadas nos campos abaixo">
      <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      <Selection position={position} onSelect={onSelect} disabled={disabled} />
    </MapContainer>
  </div>
}
