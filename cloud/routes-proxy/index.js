// MS14: the route proxy. One POST, one distance matrix. The Routes API key stays here, never in the APK.
// Request:  {"places":[{"name":"Khulna","lat":22.84,"lon":89.54}, ...]}   (2 to 10 places)
// Response: {"km":[[0, 270.4, ...], ...]}   in the order asked. A pair with no route makes the whole answer 502.
const functions = require('@google-cloud/functions-framework');

const MAX_PLACES = 10;
const ENDPOINT = 'https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix';

function valid(places) {
  if (!Array.isArray(places) || places.length < 2 || places.length > MAX_PLACES) return false;
  return places.every(p => p && typeof p.lat === 'number' && typeof p.lon === 'number'
    && p.lat >= -90 && p.lat <= 90 && p.lon >= -180 && p.lon <= 180);
}

functions.http('routeMatrix', async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'POST only' });
  const key = process.env.ROUTES_API_KEY;
  if (!key) return res.status(500).json({ error: 'The proxy has no ROUTES_API_KEY.' });
  const places = req.body && req.body.places;
  if (!valid(places)) return res.status(400).json({ error: `Send 2 to ${MAX_PLACES} places with lat and lon.` });

  const waypoint = p => ({ waypoint: { location: { latLng: { latitude: p.lat, longitude: p.lon } } } });
  const body = { origins: places.map(waypoint), destinations: places.map(waypoint), travelMode: 'DRIVE' };
  const upstream = await fetch(ENDPOINT, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Goog-Api-Key': key,
      'X-Goog-FieldMask': 'originIndex,destinationIndex,distanceMeters,condition',
    },
    body: JSON.stringify(body),
  });
  if (!upstream.ok) return res.status(502).json({ error: `Routes API answered ${upstream.status}` });
  const rows = await upstream.json();
  const n = places.length;
  const km = Array.from({ length: n }, () => Array(n).fill(null));
  for (const r of rows) {
    if (r.condition === 'ROUTE_EXISTS' || r.originIndex === r.destinationIndex) {
      km[r.originIndex][r.destinationIndex] = r.originIndex === r.destinationIndex ? 0 : Math.round((r.distanceMeters || 0) / 100) / 10;
    }
  }
  if (km.some(row => row.some(v => v === null))) return res.status(502).json({ error: 'Some pair has no road route.' });
  res.json({ km });
});
