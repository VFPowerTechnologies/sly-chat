#!/usr/bin/env python
from __future__ import print_function

import sys
import errno
import json
from os import makedirs
from os.path import join, splitext, exists
from subprocess import check_output


dimensions = [
    #iphone notification 7-10
    ('iphone', 20, [2, 3]),
    #iphone settings 5-10
    ('iphone', 29, [2, 3]),
    #iphone spotlight 7-10
    ('iphone', 40, [2, 3]),
    #iphone app 7-10
    ('iphone', 60, [2, 3]),
    #ipad notifications 7-10
    ('ipad', 20, [1, 2]),
    #ipad settings 5-10
    ('ipad', 29, [1, 2]),
    #ipad spotlight 7-10
    ('ipad', 40, [1, 2]),
    #ipad app 7-10
    ('ipad', 76, [1, 2]),
    #ipad pro app (9-10)
    ('ipad', 83.5, [2]),
]


def resize(input_file, output_file, dims):
    argv = [
        'sips',
        '-z',
        str(dims[0]),
        str(dims[1]),
        input_file,
        '--out',
        output_file
    ]

    check_output(argv)


def generate_appiconset(base_image, output_dir):
    try:
        makedirs(output_dir)
    except OSError as e:
        if e.errno != errno.EEXIST:
            raise

    contents = {
        'images': [],
        'info': {
            'author': 'xcode',
            'version': 1,
        },
    }

    _, ext = splitext(base_image)
    for idiom, base_size, scales in dimensions:
        for scale in scales:
            effective_size = base_size * scale

            n = '%d' if (base_size % 1) == 0 else '%.1f'
            effective_size_text = n % base_size

            output_filename = 'icon_%s@%d%s' % (effective_size_text, scale, ext)
            output_image = join(output_dir, output_filename)

            #ignore already existing icons (we need the dup entries for contents.json)
            if exists(output_image):
                print('Skipping %s' % output_image)
                continue

            print('Generating %s' % output_image)

            dims = (effective_size, effective_size)
            resize(base_image, output_image, dims)

            contents['images'].append({
                'size': '%sx%s' % (effective_size_text, effective_size_text),
                'idiom': idiom,
                'filename': output_filename,
                'scale': '%dx' % scale,
            })

    print('Writing Contents.json')

    with open(join(output_dir, 'Contents.json'), 'wb') as fd:
        json.dump(contents, fd, indent=2)


def main():
    if len(sys.argv) != 3:
        print('base_image output_dir')
        return

    generate_appiconset(sys.argv[1], sys.argv[2] + '.appiconset')


if __name__ == '__main__':
    main()
