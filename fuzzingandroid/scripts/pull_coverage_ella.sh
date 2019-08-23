#!/bin/bash
COV_FILE_NAME=$1
ELLA_DIR=~/fuzzingandroid/output/ella_files

adb pull /mnt/sdcard/coverage .
mkdir "${ELLA_DIR}" -p
mv coverage $ELLA_DIR/$COV_FILE_NAME.ec

