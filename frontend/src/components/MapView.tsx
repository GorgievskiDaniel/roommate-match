import { useEffect } from 'react'
import { useMap } from 'react-leaflet'

export type View = { center: [number, number]; zoom: number }

/** Moves the map when a place is chosen outside it. */
export default function MapView({ view }: { view: View | null }) {
  const map = useMap()
  useEffect(() => {
    if (view) map.flyTo(view.center, view.zoom)
  }, [map, view])
  return null
}
