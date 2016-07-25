#!/usr/bin/env python3

DEBUG = False

import atexit
import threading
import time

if DEBUG:
    class _C(): pass
    gpio = _C()
    gpio.setmode = gpio.cleanup = gpio.setup = gpio.output = lambda *a: None
    gpio.BOARD = gpio.OUT = 5
else:
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
        self._step_interval = 0
        self._position = 0
        self._target = 0
        self._speed = 0
        self._time_since_last_step = 0
        self._powered = True
        self._running = False
        self._thread = None

    def move(self, target):
        self._target = target + self._position

    @property
    def target(self):
        return self._target

    @target.setter
    def target(self, val):
        self._target = val

    @property
    def speed(self):
        return self._speed

    @speed.setter
    def speed(self, val):
        if val < 0:
            e = ValueError('speed must be non-negative')
            raise e

        if val == 0:
            if self._thread and self._thread.is_alive():
                self._speed = self._step_interval = 0
                self._thread.join(timeout=0.5)

        else:
            self._speed = val
            self._step_interval = 1 / val
            if not self._thread or not self._thread.is_alive():
                self._thread = threading.Thread(target=self._run)
                self._thread.daemon = True
                self._thread.start()

    @property
    def position(self):
        return self._position

    @position.setter
    def position(self, val):
        self._position = val

    def is_on(self):
        return self._powered

    def set_power(self, powered):
        self._powered = powered
        if powered:
            gpio.output(self._pins, self._steps[self._step])
        else:
            gpio.output(self._pins, (0, 0, 0, 0))

    def _make_one_step(self):
        if self._step_interval == 0 or self._target == self._position or not self._powered:
            return

        if self._target < self._position:
            self._step = (self._step - 1) % len(self._steps)
            self._position -= 1
        else:
            self._step = (self._step + 1) % len(self._steps)
            self._position += 1

        gpio.output(self._pins, self._steps[self._step])

    def _run(self):
        while self._step_interval > 0:
            last_time = time.time()
            self._make_one_step()
            while time.time() - last_time < self._step_interval:
                time.sleep(0.001)
                if self._step_interval == 0:
                    if DEBUG:
                        print('_step_interval == 0, shutting down thread')
                    return
