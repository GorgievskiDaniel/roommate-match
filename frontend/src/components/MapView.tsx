import { useEffect } from 'react'
import { useMap } from 'react-leaflet'

type Point = [number, number]

export type View = { center: Point; zoom: number } | { points: Point[] }

const SEARCH_MAX_ZOOM = 15

/** Moves the map when a place is chosen outside it, or fits it around a set of points. */
export default function MapView({ view }: { view: View | null }) {
  const map = useMap()
  useEffect(() => {
    if (!view) return
    if ('points' in view) {
      map.flyToBounds(view.points, { maxZoom: SEARCH_MAX_ZOOM, padding: [40, 40] })
    } else {
      map.flyTo(view.center, view.zoom)
    }
  }, [map, view])
  return null
}
