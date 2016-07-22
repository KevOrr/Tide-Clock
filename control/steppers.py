#!/usr/bin/env python3

import atexit
from RPi import GPIO as gpio

class Stepper():
    _HALF_STEPS = ((1, 0, 0, 0),
                   (1, 1, 0, 0),
                   (0, 1, 0, 0),
                   (0, 1, 1, 0),
                   (0, 0 ,1, 0),
                   (0, 0, 1, 1),
                   (0, 0, 0, 1),
                   (1, 0, 0, 1))

    _FULL_STEPS = _HALF_STEPS[::2]

    def __init__(self, pins, mode='half'):
        gpio.setmode(gpio.BOARD) # use board pin numbers instead of CPU channel numbers
        atexit.register(gpio.cleanup)

        if len(pins) != 4:
            e = ValueError('Only 4 wire stepper motors supported currently')
            raise e
        gpio.setup(pins, gpio.OUT)
        self._pins = pins

        if mode not in ('half', 'full'):
            e = ValueError('mode must be \'half\' or \'full\'')
            raise e
        self._mode = mode
        self._steps = self._HALF_STEPS if mode == 'half' else self._FULL_STEPS

        self._step = 0
        this._position = 0
        self._target = 0
        self._speed = 0
        self._powered = True

    def move(self, target, relative=True):
        self._target = target
        if relative:
            self._target += self._position

    def get_target(self):
        return self._target

    def set_speed(self, speed):
        if velocity < 0:
            e = ValueError('speed must be non-negative')
            raise e
        self._speed = speed

    def get_position(self):
        return this._position

    def set_stepper_power(self, powered=True):
        self._powered = powered
        if powered:
            gpio.output(self._pins, self._steps[self._step])
        else:
            gpio.output(self._pins, (0, 0, 0, 0))

    def step(self):
        if speed == 0 or self._target == self._position or not self._powered:
            return

        if self._target < self._position:
            self._step = (self._step - 1) % len(self._steps)
        else:
            self._step = (self._step + 1) % len(self._steps)

        gpio.output(self._pins, self._steps[self._step])
