#!/usr/bin/env python3

import sys
import optparse
import json
import time

import requests

import control.steppers as steppers
import control.station_selector as station_selector
from control.util import get_abs_path
import control.config as g

LOG_FILE = get_abs_path('log')
USAGE = 'USAGE: %prog [-c LAT,LON | -s STATION_ID]'
NOAA_API_URL = 'http://tidesandcurrents.noaa.gov/api/datagetter'
MSW_API_URL = 'http://magicseaweed.com/api/{key}/forecast/?spot_id={spot}&fields={fields}'
MSW_ID_JACKSONVILLE_BEACH = 345

STEPPER_PINS_TIDE = (11, 13, 15, 16)
STEPPER_PINS_SWELL = (29, 31, 32, 33)
STEPPER_MODE = 'half'
STEPPER_SPEED = 50

_MM_PER_TOOTH = 2.0
_TEETH_PER_REV = 20
_STEPS_PER_REV = 2 * 200 # half stepping
STEPS_PER_MM = _STEPS_PER_REV / (_MM_PER_TOOTH * _TEETH_PER_REV)

DISPLAY_MM_PER_WATER_FOOT = 10

SAMPLING_DELAY = 60 # seconds

def log(text, stdout=False, stderr=False):
    with open(LOG_FILE, 'a') as f:
        text = time.asctime() + ' ' + text.rstrip('\n') + '\n'
        f.write(text)
        if stdout:
            sys.stdout.write(text)
        if stderr:
            sys.stderr.write(text)

def get_NOAA_levels(station_id):
    log('Requesting ' + NOAA_API_URL, stdout=True)
    station_data = requests.get(NOAA_API_URL, params = {
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

def get_MSW_levels(api_key, spot_id):
    url = MSW_API_URL.format(key=api_key, spot=spot_id, fields='timestamp,swell.*')
    log('Requesting %s' % url, stdout=True)
    data = requests.get(url).json()

    now = time.time()
    for prediction in sorted(data, key=lambda pred: int(pred['timestamp'])):
        if int(prediction['timestamp']) > now:
            return prediction

    e = ValueError('Could not find a prediction after time.time()')
    raise e

if __name__ == '__main__':
    try:
        log('Tide Clock started', stdout=True)

        parser = optparse.OptionParser(USAGE)
        parser.add_option('-c', '--coordinates', action='store', type='str',
                          dest='coords', help='user\s coordinates. The closest station will be selected')
        parser.add_option('-s', '--station', action='store', type='int',
                          dest='station_id', help='manually chosen station id')
        (options, args) = parser.parse_args()

        if options.station_id is not None:
            station_id = options.station_id
            log('Using station id {} as per CLI option'.format(station_id), stdout=True)
        elif options.coords is not None:
            lat, lon = (float(coord) for coord in options.coords.split(','))
            station_id = station_selector.get_closest_station(lat, lon)
            log('Got station id {} for coords {}'.format(station_id, options.coords), stdout=True)
        else:
            log('User specified incorrect options')
            parser.error('Must specify either -c or -s')

        tide_stepper = steppers.Stepper(STEPPER_PINS_TIDE, STEPPER_MODE)
        tide_stepper.speed = STEPPER_SPEED
        swell_stepper = steppers.Stepper(STEPPER_PINS_SWELL, STEPPER_MODE)
        swell_stepper.speed = STEPPER_SPEED

        while True:
            try:
                # NOAA TIDE
                info = get_NOAA_levels(station_id)
                tide_level = float(info['data'][0]['v'])
                tide_steps = int(tide_level * DISPLAY_MM_PER_WATER_FOOT * STEPS_PER_MM)
                tide_stepper.target = tide_steps
                log('Moving tide stepper to position {}'.format(tide_steps), stdout=True)

                try:
                    #MSW SWELL
                    info = get_MSW_levels(g.MSW_API_KEY, MSW_ID_JACKSONVILLE_BEACH)
                    avg_swell = (float(info['swell']['absMinBreakingHeight'])
                               + float(info['swell']['absMaxBreakingHeight'])) / 2
                    swell_steps = int((avg_swell + tide_level) * DISPLAY_MM_PER_WATER_FOOT * STEPS_PER_MM)
                    swell_stepper.target = swell_steps
                    log('Moving swell stepper to position {}'.format(swell_steps), stdout=True)
                except IndexError:
                    log('IndexError after getting MSW swell level', stderr=True)
                except requests.exceptions.RequestException:
                    log('RequestException while trying to fetch data', stderr=True)
                except Exception as e:
                    log(e, stderr=True)

            except IndexError:
                log('IndexError after getting NOAA water tide_level', stderr=True)
            except requests.exceptions.RequestException:
                log('RequestException while trying to fetch data', stderr=True)
            except Exception as e:
                log(e, stderr=True)

            for i in range(SAMPLING_DELAY):
                time.sleep(1)

    except KeyboardInterrupt:
        log('Exiting', stdout=True)
