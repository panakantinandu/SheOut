# sheout-osrm - SheOut's own routing server

Every fare is priced from a real road route. That used to come from
`router.project-osrm.org`, the OSRM project's demonstration server: rate
limited, no uptime promise, and not meant for a commercial service. This is
our own instance, deployed on Render as the private service `sheout-osrm`
(see `render.yaml`), reached only by `sheout-backend`.

## What it holds

Every car road within 110 km of central Hyderabad, cut from the day's
Telangana extract (download.openstreetmap.fr) by `clip_region.py`: the city,
Nalgonda (~90 km), Siddipet, Medak, Mahbubnagar, Jangaon, Bhongir and the
highways between them, the nodes those roads use, and their turn
restrictions. Preprocessed with OSRM v6.0.0's car profile - the same profile
the public server runs, so distances match - with its `exclude` classes
removed (see the Dockerfile).

### Why 110 km, and not all of Telangana

The router holds the whole map in memory on a 512 MB Starter instance, so
the radius is a memory budget. The stock profile's three exclude classes
(toll, motorway, ferry) make MLD keep four sets of cell metrics; SheOut never
sends `exclude=`, so dropping them changes no route and frees the most
memory of anything. Measured on 2026-09-30 (Windows, OSRM v6.0.0; Render's
Linux figure for the old map was ~330 MB, about 1.3x these):

| map | profile | routing data | router memory |
|---|---|---|---|
| old: 37 km Hyderabad box | stock | 362 MB | 254 MB |
| **110 km circle (this)** | **lean** | **435 MB** | **266 MB** |
| 130 km circle | lean | 498 MB | 306 MB |
| 150 km circle | lean | 578 MB | 356 MB |
| 150 km circle | stock | 691 MB | 487 MB |

110 km costs about what the old map did, which Starter is proven to hold.
130 km would likely fit too, at ~400 MB on Render, but with less room than a
service nobody watches overnight should run with. Going further - Warangal,
Karimnagar, all of Telangana - means a bigger instance (Standard, 2 GB), not
a bigger clip on this one.

Routes inside Hyderabad are identical to the metre between the old map and
this one (city centre → HITEC City, Charminar → airport, Secunderabad →
Gachibowli, Madhapur → Kondapur). Hyderabad → Nalgonda is 100.6 km, 88 min.

## Outside the map

OSRM snaps a point to the nearest road it has, however far away. The backend
therefore asks with `radiuses=1000;1000`: a pickup or drop more than a
kilometre from any road in this map gets `NoSegment`, and the fare falls back
to the straight-line estimate (marked `routed: false`) instead of being priced
on a road at the edge of the map. `SERVICE_RADIUS_KM` (100 km in render.yaml)
keeps every bookable point 10 km inside the map. **Widening the service area
means widening `RADIUS_KM` in `clip_region.py` too, and measuring memory
again.**

## Refreshing the map

The image downloads the latest extract when it is built. Redeploy
`sheout-osrm` (Manual Deploy → "Clear build cache & deploy") to pick up new
roads; monthly is plenty.

## Running it locally (Windows, no Docker)

OSRM v6.0.0 publishes Windows binaries
(`node_osrm-v6.0.0-8-win32-x64-Release.tar.gz` on its GitHub release). They
need `tbb12.dll` and `bz2.dll` beside them (conda-forge `tbb` and `bzip2`;
rename `libbz2.dll` to `bz2.dll`). Then:

    python clip_region.py telangana.osm.pbf region.osm.pbf   # pip install osmium
    osrm-extract -p car_sheout.lua region.osm.pbf   # car.lua with the Dockerfile's sed applied
    osrm-partition region.osrm
    osrm-customize region.osrm
    osrm-routed --algorithm mld --port 5000 region.osrm

and start the backend with `OSRM_BASE_URL=http://localhost:5000`.
