#!/bin/sh

#CLASS_FILES=~/fuzzingandroid/apps/tasks/app/build/intermediates/javac/amazonDebug/classes/
CLASS_FILES=$1
OUTPUT_DIR=$2

EC_DIR=$OUTPUT_DIR/ec_files

JACOCO_PATH=`realpath ../../libs/jacococli.jar`


#~/fuzzingandroid/scripts/pull_coverage.sh coverage_temp

if [ -z "$(ls -A $EC_DIR)" ]; then
    echo "so far no ec files are generated."
    exit 0
fi      

EC_FILES=""

for EC in $EC_DIR/*
do 
        EC_FILES=$EC_FILES" $EC "
done

echo $EC_FILES

var=`date +"%T"`
java -jar $JACOCO_PATH report $EC_FILES $CLASS_FILES --xml $OUTPUT_DIR/coverage.xml
cp $OUTPUT_DIR/coverage.xml $OUTPUT_DIR/coverage_$var.xml
