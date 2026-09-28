/**
 * SheOut's map look, by day: a real, colourful city map - green parks, blue
 * water, warm main roads, readable street and area names - with the clutter
 * taken out.
 * <p>
 * An earlier version greyed everything out so the purple route would be the
 * only colour. On a phone in daylight that read as a blank, lifeless box and
 * made the map harder to use: a partner finds a pickup by the lake, the park
 * or the main road next to it, and those need to look like a lake, a park
 * and a main road. So:
 * <ul>
 *   <li>shops, restaurants, ATMs and bus stops are hidden - they are the noise
 *       that competes with the pins; landmarks, hospitals, parks and places of
 *       worship stay, because people give directions by them;</li>
 *   <li>parks are a clear green and water a clear blue, main roads a warm
 *       yellow with an amber edge, other roads white on a warm off-white land;</li>
 *   <li>street names stay on, in a dark grey that reads in sunlight.</li>
 * </ul>
 * The route is drawn in brand purple on a white casing (see LiveMap), which
 * stands out against all of these.
 * <p>
 * Applied as a JSON style. If VITE_GOOGLE_MAPS_MAP_ID is set, the map uses
 * that Cloud Console style instead (Google ignores JSON styles on a map with
 * a Map ID), so the look can later be managed without a code change.
 */
export const SHEOUT_MAP_STYLE: google.maps.MapTypeStyle[] = [
  { featureType: 'landscape.man_made', elementType: 'geometry', stylers: [{ color: '#f6f4ef' }] },
  { featureType: 'landscape.natural', elementType: 'geometry', stylers: [{ color: '#eef3e4' }] },
  { elementType: 'labels.text.fill', stylers: [{ color: '#4f535a' }] },
  { elementType: 'labels.text.stroke', stylers: [{ color: '#ffffff' }, { weight: 3 }] },

  // The noise: businesses and bus stops. Landmarks, hospitals and parks stay.
  { featureType: 'poi.business', stylers: [{ visibility: 'off' }] },
  { featureType: 'transit.station.bus', stylers: [{ visibility: 'off' }] },
  { featureType: 'poi', elementType: 'labels.text.fill', stylers: [{ color: '#6b6f76' }] },
  { featureType: 'poi.park', elementType: 'geometry', stylers: [{ color: '#c5e8b7' }] },
  { featureType: 'poi.park', elementType: 'labels.text.fill', stylers: [{ color: '#3f7f35' }] },
  { featureType: 'administrative.land_parcel', stylers: [{ visibility: 'off' }] },
  { featureType: 'administrative.locality', elementType: 'labels.text.fill', stylers: [{ color: '#2f3237' }] },
  { featureType: 'administrative.neighborhood', elementType: 'labels.text.fill', stylers: [{ color: '#5f636a' }] },

  { featureType: 'road', elementType: 'geometry.fill', stylers: [{ color: '#ffffff' }] },
  { featureType: 'road', elementType: 'geometry.stroke', stylers: [{ color: '#dcdad4' }] },
  { featureType: 'road.highway', elementType: 'geometry.fill', stylers: [{ color: '#fde7a3' }] },
  { featureType: 'road.highway', elementType: 'geometry.stroke', stylers: [{ color: '#efc56a' }] },
  { featureType: 'road.arterial', elementType: 'geometry.fill', stylers: [{ color: '#fffdf6' }] },
  { featureType: 'road.arterial', elementType: 'geometry.stroke', stylers: [{ color: '#e6dcc2' }] },
  { featureType: 'road', elementType: 'labels.text.fill', stylers: [{ color: '#5a5e65' }] },
  { featureType: 'road.highway', elementType: 'labels.icon', stylers: [{ visibility: 'off' }] },

  { featureType: 'transit.line', elementType: 'geometry', stylers: [{ color: '#c9c4d6' }] },

  { featureType: 'water', elementType: 'geometry', stylers: [{ color: '#a7d3f5' }] },
  { featureType: 'water', elementType: 'labels.text.fill', stylers: [{ color: '#2f6fa8' }] },
];

/**
 * The same map, at night.
 * <p>
 * The same decisions as the light style - no points of interest, no transit,
 * parks kept as shapes to orient by - on a dark ground, so a tracking screen
 * at 11pm is not a white rectangle in a dark app. Roads are lighter than the
 * land rather than darker, because on a dark map the road network is what
 * has to read first, and water is a deep blue that cannot be mistaken for
 * land.
 */
export const SHEOUT_MAP_STYLE_DARK: google.maps.MapTypeStyle[] = [
  { elementType: 'geometry', stylers: [{ color: '#1a1530' }] },
  { elementType: 'labels.icon', stylers: [{ visibility: 'off' }] },
  { elementType: 'labels.text.fill', stylers: [{ color: '#9d95b8' }] },
  { elementType: 'labels.text.stroke', stylers: [{ color: '#14101f' }, { weight: 3 }] },

  { featureType: 'poi', stylers: [{ visibility: 'off' }] },
  { featureType: 'poi.park', elementType: 'geometry', stylers: [{ visibility: 'on' }, { color: '#1d2a26' }] },
  { featureType: 'transit', stylers: [{ visibility: 'off' }] },
  { featureType: 'administrative.land_parcel', stylers: [{ visibility: 'off' }] },
  { featureType: 'administrative', elementType: 'geometry', stylers: [{ visibility: 'off' }] },

  { featureType: 'road', elementType: 'geometry.fill', stylers: [{ color: '#2b2445' }] },
  { featureType: 'road', elementType: 'geometry.stroke', stylers: [{ color: '#221c39' }] },
  { featureType: 'road.highway', elementType: 'geometry.fill', stylers: [{ color: '#3a2f5c' }] },
  { featureType: 'road.highway', elementType: 'geometry.stroke', stylers: [{ color: '#241d3c' }] },
  { featureType: 'road.local', elementType: 'labels.text.fill', stylers: [{ color: '#8c84a6' }] },

  { featureType: 'water', elementType: 'geometry', stylers: [{ color: '#121a2e' }] },
];
