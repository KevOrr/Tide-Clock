#!/usr/bin/env python3

import sys
import json
from bs4 import BeautifulSoup as BS
import requests

URL_SCRAPE = 'https://tidesandcurrents.noaa.gov/stations.html?type=All%20Stations&sort=0'
URL_API = 'http://tidesandcurrents.noaa.gov/api/datagetter'
OUT_FILE = 'stations.json'

print('Getting station ids...')
soup = BS(requests.get(URL_SCRAPE).text, 'lxml')
stations = [int(station.attrs['id'].strip('a')) for station in soup.find_all(class_='station')]
print('Found %d stations' % len(stations))

good_stations = {}

for i, station in enumerate(stations):
    print(station, end=': ')
    station_data = requests.get(URL_API, params = {
        'station': station,
        'date': 'latest',
        'product': 'water_level',
        'datum': 'mllw',
        'units': 'english',
        'time_zone': 'lst_ldt',
        'format': 'json',
        'application': 'Tide Clock Test'
    }).json()

    if 'error' in station_data:
        print('Bad' + ' '*14)

    elif 'metadata' in station_data and 'data' in station_data:
        print('Good' + ' '*13)
        station_data['metadata']['lat'] = float(station_data['metadata']['lat'])
        station_data['metadata']['lon'] = float(station_data['metadata']['lon'])
        good_stations[station_data['metadata']['id']] = station_data['metadata']

    else:
        e = ValueError('Malformed response for station id %s' % station)
        raise e

    sys.stdout.write('{}/{} ({:.1%})\r'.format(i, len(stations), i/len(stations)))

with open(OUT_FILE, 'w') as f:
    json.dump(good_stations, f)
