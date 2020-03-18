# -*- coding: utf-8 -*-
"""
Spyder Editor

This is a temporary script file.
"""
from functools import reduce

def count_challenge(n):    
    count = int(4 * n ** 0.5)
    blocks_broken = int (n * 0.01)
    temp = [(n - blocks_broken - i) / n for i in range(count)]
    for i in range(count):
        prob_fail = reduce(lambda x, y: x * y, temp[0:i+1])
        if prob_fail <= 0.01:
            return [i, 1 - prob_fail]

n = [10 * (2 ** 20) / 223, 20 * (2 ** 20) / 223, 50 * (2 ** 20) / 223, 100 * (2 ** 20) / 223, 200 * (2 ** 20) / 223, 500 * (2 ** 20) / 223, 1 * (2 ** 30) / 223, 1.5 * (2 ** 30) / 223]
print ([count_challenge(i) for i in n])