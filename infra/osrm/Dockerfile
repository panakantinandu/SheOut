# SheOut's own routing server: OSRM with the roads within 110 km of
# Hyderabad - the city, Nalgonda, Siddipet, Medak, Mahbubnagar and between.
#
# Built entirely at image-build time, so the running container only serves:
#   1. download the day's Telangana extract (~100 MB) and clip the region
#      out of it (~21 MB) - clip_region.py;
#   2. preprocess it for OSRM's multi-level Dijkstra with the car profile,
#      the same profile router.project-osrm.org uses, so fares match - less
#      its exclude classes (see below);
#   3. serve it. ~435 MB of routing data, ~270 MB of it resident.
#
# Each deploy rebuilds from the latest extract, so redeploying is how the
# map is refreshed. See README.md in this folder.

# ---- 1. clip ---------------------------------------------------------------
FROM python:3.12-slim AS clip
# The osmium wheel bundles bz2 and lz4 but links expat, zlib and the C++
# runtime from the system, and the slim image leaves out expat. The import
# check fails the build here, in one line, if another one goes missing.
RUN apt-get update \
 && apt-get install -y --no-install-recommends libexpat1 libstdc++6 zlib1g \
 && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir osmium==4.3.1 && python -c "import osmium"
WORKDIR /data
# Changing this (or redeploying without the build cache) fetches a fresh map.
ARG EXTRACT_URL=http://download.openstreetmap.fr/extracts/asia/india/telangana-latest.osm.pbf
RUN python -c "import urllib.request, sys; urllib.request.urlretrieve(sys.argv[1], 'telangana.osm.pbf')" "$EXTRACT_URL"
COPY clip_region.py .
RUN python clip_region.py telangana.osm.pbf region.osm.pbf && rm telangana.osm.pbf

# ---- 2. preprocess -----------------------------------------------------------
FROM ghcr.io/project-osrm/osrm-backend:v6.0.0 AS build
COPY --from=clip /data/region.osm.pbf /data/region.osm.pbf
# The car profile, with no exclude classes. Stock, it lets a request say
# exclude=toll, motorway or ferry, and MLD pays for that with a full extra
# set of cell metrics per class - four in all, the largest thing in memory.
# SheOut never sends exclude=, so routes are identical without them, and the
# router holds six times the area in about the same memory. The grep fails the
# build if a newer image's car.lua no longer matches the edit.
RUN sed 's/^\( *\)excludable = Sequence {$/\1excludable = Sequence {},\n\1_unused_excludable = Sequence {/' \
        /opt/car.lua > /opt/car_sheout.lua \
 && grep -q '_unused_excludable' /opt/car_sheout.lua
RUN osrm-extract -p /opt/car_sheout.lua /data/region.osm.pbf \
 && osrm-partition /data/region.osrm \
 && osrm-customize /data/region.osrm \
 && rm /data/region.osm.pbf

# ---- 3. serve ----------------------------------------------------------------
FROM ghcr.io/project-osrm/osrm-backend:v6.0.0
COPY --from=build /data /data
EXPOSE 5000
# Render sets PORT; 5000 is OSRM's usual port for anywhere else.
# --max-table-size is kept small: nothing in SheOut asks for distance tables,
# and a large one is the cheapest way to make this server do a lot of work.
CMD ["sh", "-c", "exec osrm-routed --algorithm mld --port ${PORT:-5000} --max-table-size 100 /data/region.osrm"]
