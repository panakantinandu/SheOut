/**
 * SheOut's map look: quiet enough that the app's own markers and route are
 * what the eye lands on.
 * <p>
 * Google's default style is built for finding businesses - every café, ATM
 * and bus stop gets an icon and a label. On a screen whose only job is "where
 * is my pickup, where is my partner", that is noise competing with the three
 * things that matter. So:
 * <ul>
 *   <li>points of interest and transit are hidden, parks stay as soft green
 *       shapes (they are how people orient themselves);</li>
 *   <li>land is a very pale lavender, roads white with a light purple edge
 *       for the big ones, so the brand purple route reads as "your way"
 *       rather than as one more road;</li>
 *   <li>road shields and parcel lines are gone, road and area names stay,
 *       in a muted grey.</li>
 * </ul>
 * Applied as a JSON style. If VITE_GOOGLE_MAPS_MAP_ID is set, the map uses
 * that Cloud Console style instead (Google ignores JSON styles on a map with
 * a Map ID), so the look can later be managed without a code change.
 */
export const SHEOUT_MAP_STYLE: google.maps.MapTypeStyle[] = [
  { elementType: 'geometry', stylers: [{ color: '#f6f3fa' }] },
  { elementType: 'labels.icon', stylers: [{ visibility: 'off' }] },
  { elementType: 'labels.text.fill', stylers: [{ color: '#6f6883' }] },
  { elementType: 'labels.text.stroke', stylers: [{ color: '#ffffff' }, { weight: 3 }] },

  { featureType: 'poi', stylers: [{ visibility: 'off' }] },
  { featureType: 'poi.park', elementType: 'geometry', stylers: [{ visibility: 'on' }, { color: '#e2efdf' }] },
  { featureType: 'transit', stylers: [{ visibility: 'off' }] },
  { featureType: 'administrative.land_parcel', stylers: [{ visibility: 'off' }] },
  { featureType: 'administrative', elementType: 'geometry', stylers: [{ visibility: 'off' }] },

  { featureType: 'road', elementType: 'geometry.fill', stylers: [{ color: '#ffffff' }] },
  { featureType: 'road', elementType: 'geometry.stroke', stylers: [{ color: '#ebe6f2' }] },
  { featureType: 'road.highway', elementType: 'geometry.fill', stylers: [{ color: '#efe8fa' }] },
  { featureType: 'road.highway', elementType: 'geometry.stroke', stylers: [{ color: '#d9cdee' }] },
  { featureType: 'road.local', elementType: 'labels.text.fill', stylers: [{ color: '#9a94ab' }] },

  { featureType: 'water', elementType: 'geometry', stylers: [{ color: '#d5e3f1' }] },
  { featureType: 'water', elementType: 'labels.text.fill', stylers: [{ color: '#8aa2bd' }] },
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
