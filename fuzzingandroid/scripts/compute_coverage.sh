#!/bin/bash

EMMA_DIR=$FUZZER/emma_jars
EC_DIR=$FUZZER/output/ec_files
OUTPUT=$FUZZER/output/

#~/fuzzingandroid/scripts/pull_coverage.sh coverage_temp

if [ -z "$(ls -A $EC_DIR)" ]; then
    echo "so far no ec files are generated."
    exit 0
fi      

EC_FILES=""

for EC in $EC_DIR/*
do 
        EC_FILES=$EC_FILES"-in $EC "
done

echo $EC_FILES



var=`date +"%T"`
java -classpath $EMMA_DIR/emma_device.jar emma report -r txt $EC_FILES -in $EMMA_DIR/coverage.em  -Dreport.txt.out.file=$OUTPUT/coverage.txt
cp $OUTPUT/coverage.txt $OUTPUT/coverage_$var.txt
