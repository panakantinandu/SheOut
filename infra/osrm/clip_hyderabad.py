"""Cuts the Hyderabad metro area out of a larger OSM extract, for OSRM.

Keeps every road (highway=*) with at least one node inside the box, every
node those roads use (even outside it, so no road is cut mid-way), and the
turn restrictions on them. That is all OSRM's car profile reads.

    python clip_hyderabad.py telangana-latest.osm.pbf hyderabad.osm.pbf

The box is greater Hyderabad with margin: about 37 km out from the centre
on every side - the whole Outer Ring Road
(about 22 km) and the airport, plus room for a route
that has to go around something. Keep SERVICE_RADIUS_KM well inside it -
see infra/osrm/README.md.
"""
import sys
import time

import osmium

SOUTH, WEST, NORTH, EAST = 17.05, 78.13, 17.72, 78.85


def main(src: str, dst: str) -> None:
    started = time.time()
    inside = osmium.IdTracker()
    for node in osmium.FileProcessor(src, osmium.osm.NODE):
        loc = node.location
        if SOUTH <= loc.lat <= NORTH and WEST <= loc.lon <= EAST:
            inside.add_node(node.id)

    kept_ways = osmium.IdTracker()
    ways = relations = 0
    with osmium.BackReferenceWriter(dst, ref_src=src, overwrite=True) as writer:
        for obj in osmium.FileProcessor(src, osmium.osm.WAY | osmium.osm.RELATION):
            if obj.is_way():
                if 'highway' in obj.tags and inside.contains_any_references(obj):
                    writer.add_way(obj)
                    kept_ways.add_way(obj.id)
                    ways += 1
            elif obj.tags.get('type') == 'restriction' and kept_ways.contains_any_references(obj):
                writer.add_relation(obj)
                relations += 1
    print(f'{ways} roads, {relations} turn restrictions, {time.time() - started:.0f}s')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
