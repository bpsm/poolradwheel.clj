#!/bin/bash

# The images in src/images need to be scaled and copied
# to src/main/resources before they can be used. 
# This script uses convert (http://www.imagemagick.org/) to do this.

set -e

filter=Box

function copy-scaled() {
    size=$1
    sdir=$2
    tdir=$3

    for spng in $sdir/*.png
    do
        tpng=$tdir/$(basename $spng)
        convert $spng -filter $filter -resize $size $tpng
    done
}

s=src/images
t=src/main/resources/poolradwheel

copy-scaled 24 $s/espuar $t/0
copy-scaled 24 $s/dethek $t/1
cp $s/spirals/*.png $t/2
