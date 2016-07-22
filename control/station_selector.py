#!/usr/bin/env python3

import sys
from math import sin, cos, acos, radians, sqrt, pi
import json

USAGE = '%s lat lon' % sys.argv[0]
IN_FILE = 'stations.json'

# https://en.wikipedia.org/wiki/Great-circle_distance#Formulas
# All angles in radians
def get_angle(lat1, lon1, lat2, lon2):
    return acos(sin(lat1)*sin(lat2) + cos(lat1)*cos(lat2)*cos(lon2 - lon1))
    #dlat = lat1 - lat2
    #dlon = lon1 - lon2
    #return 2*asin(sqrt(sin(dlat/2)**2 + cos(lat1)*cos(lat2)*sin(dlon/2)**2))

# All angles in degrees
def get_closest_station(lat, lon):
    lat = radians(lat)
    lon = radians(lon)

    with open(IN_FILE) as f:
        stations = json.load(f)

    min_angle = 2*pi
    for station, meta in stations.items():
        angle = get_angle(lat, lon, radians(float(meta['lat'])), radians(float(meta['lon'])))
        if angle <= min_angle:
            min_angle = angle
            closest_station = station

    return closest_station

if __name__ == '__main__':
    try:
        lat = float(sys.argv[1])
        lon = float(sys.argv[2])
    except (IndexError, ValueError):
        print(USAGE)
        sys.exit(1)

    with open(IN_FILE) as f:
        stations = json.load(f)

    closest_station = get_closest_station(lat, lon)

    print('{}: {}'.format(closest_station, stations[closest_station]['name']))
    print('{}, {}'.format(stations[closest_station]['lat'], stations[closest_station]['lon']))
