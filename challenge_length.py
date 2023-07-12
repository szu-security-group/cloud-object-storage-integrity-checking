# -*- coding: utf-8 -*-
# this is a script to compute the challenge length

from functools import reduce


def count_challenge(n):
    count = int(4 * n ** 0.5)  # upper bound for the challenge lentgh
    blocks_broken = int(n * 0.01)
    temp = [(n - blocks_broken - i) / n for i in range(count)]
    for i in range(count):
        prob_fail = reduce(lambda x, y: x * y, temp[0:i + 1])
        if prob_fail <= 0.01:
            return [i, 1 - prob_fail]


block_size = 1024
n = [1 * (2 ** 30) / block_size,
     5 * (2 ** 30) / block_size,
     10 * (2 ** 30) / block_size,
     15 * (2 ** 30) / block_size,
     20 * (2 ** 30) / block_size,
     40 * (2 ** 30) / block_size]
print([count_challenge(i) for i in n])
