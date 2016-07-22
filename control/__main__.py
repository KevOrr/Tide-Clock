#!/usr/bin/env python3

import sys
import optparse
import json
import requests

#import steppers
import station_selector

URL_NOAA_API = 'http://tidesandcurrents.noaa.gov/api/datagetter'

def get_NOAA_levels(station_id):
    station_data = requests.get(URL_NOAA_API, params = {
        'station': station_id,
        'date': 'latest',
        'product': 'water_level',
        'datum': 'mllw',
        'units': 'english',
        'time_zone': 'lst_ldt',
        'format': 'json',
        'application': 'Tide Clock Test'
    }).json()

    return station_data

if __name__ == '__main__':
    USAGE = 'USAGE: %prog [-c LAT,LON | -s STATION_ID]'
    parser = optparse.OptionParser(USAGE)
    parser.add_option('-c', '--coordinates', action='store', type='str',
                      dest='coords', help='user\s coordinates. The closest station will be selected')
    parser.add_option('-s', '--station', action='store', type='int',
                      dest='station_id', help='manually chosen station id')
    (options, args) = parser.parse_args()

    if options.station_id is not None:
        station_id = options.station_id
    elif options.coords is not None:
        lat, lon = (float(coord) for coord in options.coords.split(','))
        station_id = station_selector.get_closest_station(lat, lon)
    else:
        parser.error('Must specify either -c or -s')

    try:
        water_level = get_NOAA_levels(station_id)
        print(water_level)
    except (IndexError, ValueError):
        print(USAGE)
        sys.exit(1)
