#!/usr/bin/env python3

import os.path

def get_abs_path(rel_path):
    return os.path.join(os.path.dirname(__file__), rel_path)
