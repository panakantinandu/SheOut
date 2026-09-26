# sheout-osrm - SheOut's own routing server

Every fare is priced from a real road route. That used to come from
`router.project-osrm.org`, the OSRM project's demonstration server: rate
limited, no uptime promise, and not meant for a commercial service. This is
our own instance, deployed on Render as the private service `sheout-osrm`
(see `render.yaml`), reached only by `sheout-backend`.

## What it holds

Greater Hyderabad's roads, cut from the day's Telangana extract
(download.openstreetmap.fr) by `clip_hyderabad.py`: every road with a node
inside 17.05-17.72 N, 78.13-78.85 E (about 37 km from the city centre on every
side - the whole Outer Ring Road and the airport), the nodes those roads use,
and their turn restrictions. Preprocessed with OSRM v6.0.0's car profile -
the same profile the public server runs, so distances match it.

Measured on 2026-09-26:

| | Hyderabad (this) | All of Telangana |
|---|---|---|
| map data | 14.6 MB | 98.8 MB |
| routing data | 362 MB | 906 MB |
| router memory when loaded | ~330 MB | ~615 MB |
| peak memory while building | 663 MB | 1,610 MB |
| build time (extract + partition + customize) | ~66 s | ~248 s |

Hyderabad-only fits Render's Starter (512 MB); all of Telangana does not.

Same routes, compared on 2026-09-26:

| route | public demo server | this server |
|---|---|---|
| Madhapur → Kondapur | 3.84 km, 5.8 min | 3.84 km, 5.7 min |
| city centre (Abids) → HITEC City | 16.32 km, 21.9 min | 16.32 km, 21.8 min |
| Secunderabad → Gachibowli | 21.13 km, 27.9 min | 21.13 km, 27.7 min |
| centre → airport | 22.73 km, 28.2 min | 22.73 km, 28.1 min |

## Outside the map

OSRM snaps a point to the nearest road it has, however far away. The backend
therefore asks with `radiuses=1000;1000`: a pickup or drop more than a
kilometre from any road in this map gets `NoSegment`, and the fare falls back
to the straight-line estimate (marked `routed: false`) instead of being priced
on a road at the edge of the map. `SERVICE_RADIUS_KM` (25 km in render.yaml)
keeps every bookable point well inside the box. **Widening the service area
past ~30 km means widening the box in `clip_hyderabad.py` too.**

## Refreshing the map

The image downloads the latest extract when it is built. Redeploy
`sheout-osrm` (Manual Deploy → "Clear build cache & deploy") to pick up new
roads; monthly is plenty.

## Running it locally (Windows, no Docker)

OSRM v6.0.0 publishes Windows binaries
(`node_osrm-v6.0.0-8-win32-x64-Release.tar.gz` on its GitHub release). They
need `tbb12.dll` and `bz2.dll` beside them (conda-forge `tbb` and `bzip2`;
rename `libbz2.dll` to `bz2.dll`). Then:

    python clip_hyderabad.py telangana.osm.pbf hyderabad.osm.pbf   # pip install osmium
    osrm-extract -p car.lua hyderabad.osm.pbf
    osrm-partition hyderabad.osrm
    osrm-customize hyderabad.osrm
    osrm-routed --algorithm mld --port 5000 hyderabad.osrm

and start the backend with `OSRM_BASE_URL=http://localhost:5000`.
