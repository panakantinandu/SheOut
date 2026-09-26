# SheOut's own routing server: OSRM with greater Hyderabad's roads.
#
# Built entirely at image-build time, so the running container only serves:
#   1. download the day's Telangana extract (~100 MB) and clip greater
#      Hyderabad out of it (~15 MB) - clip_hyderabad.py;
#   2. preprocess it for OSRM's multi-level Dijkstra with the car profile,
#      the same profile router.project-osrm.org uses, so fares match;
#   3. serve it. ~360 MB of routing data, held in memory.
#
# Each deploy rebuilds from the latest extract, so redeploying is how the
# map is refreshed. See README.md in this folder.

# ---- 1. clip ---------------------------------------------------------------
FROM python:3.12-slim AS clip
RUN pip install --no-cache-dir osmium==4.3.1
WORKDIR /data
# Changing this (or redeploying without the build cache) fetches a fresh map.
ARG EXTRACT_URL=http://download.openstreetmap.fr/extracts/asia/india/telangana-latest.osm.pbf
RUN python -c "import urllib.request, sys; urllib.request.urlretrieve(sys.argv[1], 'telangana.osm.pbf')" "$EXTRACT_URL"
COPY clip_hyderabad.py .
RUN python clip_hyderabad.py telangana.osm.pbf hyderabad.osm.pbf && rm telangana.osm.pbf

# ---- 2. preprocess -----------------------------------------------------------
FROM ghcr.io/project-osrm/osrm-backend:v6.0.0 AS build
COPY --from=clip /data/hyderabad.osm.pbf /data/hyderabad.osm.pbf
RUN osrm-extract -p /opt/car.lua /data/hyderabad.osm.pbf \
 && osrm-partition /data/hyderabad.osrm \
 && osrm-customize /data/hyderabad.osrm \
 && rm /data/hyderabad.osm.pbf

# ---- 3. serve ----------------------------------------------------------------
FROM ghcr.io/project-osrm/osrm-backend:v6.0.0
COPY --from=build /data /data
EXPOSE 5000
# Render sets PORT; 5000 is OSRM's usual port for anywhere else.
# --max-table-size is kept small: nothing in SheOut asks for distance tables,
# and a large one is the cheapest way to make this server do a lot of work.
CMD ["sh", "-c", "exec osrm-routed --algorithm mld --port ${PORT:-5000} --max-table-size 100 /data/hyderabad.osrm"]
