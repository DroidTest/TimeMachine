#!/bin/bash

COV_FILE_NAME=$1

EC_DIR=$FUZZER/output/ec_files
EMMA_DIR=$FUZZER/emma_jars

adb shell am broadcast -a edu.gatech.m3.emma.COLLECT_COVERAGE
adb pull /mnt/sdcard/coverage.ec
#adb shell rm /mnt/sdcard/coverage.ec

java -classpath $EMMA_DIR/emma_device.jar emma report -r txt -in coverage.ec -in $EMMA_DIR/coverage.em &> temp

if grep -q -i "exception" temp
then
    echo "Error - removing invalid coverage.ec"
    rm coverage.ec
    adb shell rm /mnt/sdcard/coverage.ec
else
    echo "ec file is valid!"
    mv coverage.ec $EC_DIR/$COV_FILE_NAME.ec
fi
