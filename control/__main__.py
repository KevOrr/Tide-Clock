#!/usr/bin/env python3

import sys
import optparse
import json
import time

import requests

import steppers
import station_selector

LOG_FILE = 'log'
URL_NOAA_API = 'http://tidesandcurrents.noaa.gov/api/datagetter'

STEPPER_PINS = (11, 13, 15, 16)
STEPPER_MODE = 'half'
STEPPER_SPEED = 50

_MM_PER_TOOTH = 2.0
_TEETH_PER_REV = 20
_STEPS_PER_REV = 2 * 200 # half stepping
STEPS_PER_MM = _STEPS_PER_REV / (_MM_PER_TOOTH * _TEETH_PER_REV)

DISPLAY_MM_PER_WATER_FOOT = 10

def log(text):
    with open(LOG_FILE, 'a') as f:
        f.write(time.asctime + ' ' + text.rstrip('\n') + '\n')

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

    stepper = steppers.Stepper(STEPPER_PINS, STEPPER_MODE)
    stepper.speed = STEPPER_SPEED

    while True:
        try:
            info = get_NOAA_levels(station_id)
            level = info['data'][0]['v']
            steps = level / DISPLAY_MM_PER_WATER_FOOT * STEPS_PER_MM
            stepper.target = steps
        except IndexError:
            log('IndexError after getting NOAA water level', stderr=True)
        except requests.exceptions.RequestException:
            log('RequestException while trying to fetch data', stderr=True)
