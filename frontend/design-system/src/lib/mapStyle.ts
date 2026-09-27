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
 *   <li>land is a neutral light grey and roads white with a faint edge, so
 *       the brand purple route and pins are the only colour on the map and
 *       read as "your way" rather than as one more road;</li>
 *   <li>road shields, parcel lines and the names of small lanes are gone;
 *       main road and area names stay, in a soft grey.</li>
 * </ul>
 * Applied as a JSON style. If VITE_GOOGLE_MAPS_MAP_ID is set, the map uses
 * that Cloud Console style instead (Google ignores JSON styles on a map with
 * a Map ID), so the look can later be managed without a code change.
 */
export const SHEOUT_MAP_STYLE: google.maps.MapTypeStyle[] = [
  // Neutral light grey land, not lavender: the brand purple route and pins
  // are the only purple on the map, so they are what the eye lands on.
  { elementType: 'geometry', stylers: [{ color: '#f3f3f5' }] },
  { elementType: 'labels.icon', stylers: [{ visibility: 'off' }] },
  { elementType: 'labels.text.fill', stylers: [{ color: '#8f8c99' }] },
  { elementType: 'labels.text.stroke', stylers: [{ color: '#ffffff' }, { weight: 3 }] },

  { featureType: 'poi', stylers: [{ visibility: 'off' }] },
  { featureType: 'poi.park', elementType: 'geometry', stylers: [{ visibility: 'on' }, { color: '#e5ece3' }] },
  { featureType: 'poi.park', elementType: 'labels', stylers: [{ visibility: 'off' }] },
  { featureType: 'transit', stylers: [{ visibility: 'off' }] },
  { featureType: 'administrative.land_parcel', stylers: [{ visibility: 'off' }] },
  { featureType: 'administrative.neighborhood', elementType: 'labels.text.fill', stylers: [{ color: '#a4a1ad' }] },
  { featureType: 'administrative', elementType: 'geometry', stylers: [{ visibility: 'off' }] },

  { featureType: 'road', elementType: 'geometry.fill', stylers: [{ color: '#ffffff' }] },
  { featureType: 'road', elementType: 'geometry.stroke', stylers: [{ color: '#e6e5eb' }] },
  { featureType: 'road.highway', elementType: 'geometry.fill', stylers: [{ color: '#fbfaf7' }] },
  { featureType: 'road.highway', elementType: 'geometry.stroke', stylers: [{ color: '#dedce4' }] },
  { featureType: 'road.arterial', elementType: 'labels.text.fill', stylers: [{ color: '#9d9aa6' }] },
  { featureType: 'road.local', elementType: 'labels', stylers: [{ visibility: 'off' }] },

  { featureType: 'water', elementType: 'geometry', stylers: [{ color: '#dfe6ec' }] },
  { featureType: 'water', elementType: 'labels.text.fill', stylers: [{ color: '#a3b0bd' }] },
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
