"""Cuts SheOut's operating region out of a larger OSM extract, for OSRM.

Keeps every road a car can be routed on with at least one node within
RADIUS_KM of central Hyderabad, every node those roads use (even outside the
circle, so no road is cut mid-way), and the turn restrictions on them. That
is all OSRM's car profile reads.

    python clip_region.py telangana-latest.osm.pbf region.osm.pbf

110 km reaches Nalgonda (~90 km), Siddipet, Medak, Mahbubnagar and Jangaon,
and leaves ~10 km of road beyond the 100 km service radius for a route that
has to go around something. Keep SERVICE_RADIUS_KM that far inside it - see
infra/osrm/README.md.

ONLY CAR ROADS. Footways, paths, tracks and cycleways are dropped here: the
car profile ignores them anyway, so routes are identical (checked to the
metre against the old Hyderabad map), and the extract stays small.

THE RADIUS IS A MEMORY BUDGET, not a coverage wish. The router holds the
whole map in RAM on a 512 MB instance; measured with the lean profile (see
Dockerfile), 110 km costs about what the old 37 km Hyderabad box did. 130 km
is ~15% more and 150 km ~35% more - past what Starter can hold safely.
"""
import math
import sys
import time

import osmium

CENTRE_LAT, CENTRE_LNG = 17.3850, 78.4867
RADIUS_KM = 110

CAR_ROADS = {
    'motorway', 'motorway_link', 'trunk', 'trunk_link', 'primary', 'primary_link',
    'secondary', 'secondary_link', 'tertiary', 'tertiary_link',
    'unclassified', 'residential', 'living_street', 'service',
}

# A cheap box test first, so the haversine only runs near the circle.
LAT_SPAN = RADIUS_KM / 111.19
LNG_SPAN = RADIUS_KM / (111.19 * math.cos(math.radians(CENTRE_LAT)))


def km_from_centre(lat: float, lng: float) -> float:
    p1, p2 = math.radians(CENTRE_LAT), math.radians(lat)
    a = (math.sin((p2 - p1) / 2) ** 2
         + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lng - CENTRE_LNG) / 2) ** 2)
    return 2 * 6371 * math.asin(math.sqrt(a))


def main(src: str, dst: str) -> None:
    started = time.time()
    inside = osmium.IdTracker()
    for node in osmium.FileProcessor(src, osmium.osm.NODE):
        lat, lng = node.location.lat, node.location.lon
        if (abs(lat - CENTRE_LAT) <= LAT_SPAN and abs(lng - CENTRE_LNG) <= LNG_SPAN
                and km_from_centre(lat, lng) <= RADIUS_KM):
            inside.add_node(node.id)

    kept_ways = osmium.IdTracker()
    ways = relations = 0
    with osmium.BackReferenceWriter(dst, ref_src=src, overwrite=True) as writer:
        for obj in osmium.FileProcessor(src, osmium.osm.WAY | osmium.osm.RELATION):
            if obj.is_way():
                if obj.tags.get('highway') in CAR_ROADS and inside.contains_any_references(obj):
                    writer.add_way(obj)
                    kept_ways.add_way(obj.id)
                    ways += 1
            elif obj.tags.get('type') == 'restriction' and kept_ways.contains_any_references(obj):
                writer.add_relation(obj)
                relations += 1
    print(f'{ways} roads, {relations} turn restrictions, {time.time() - started:.0f}s')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
