#!/bin/bash


PROJECT_DIR=$FUZZER
pwd
timestamp=$(date +"%Y-%m-%d_%H-%M-%S")
tar --ignore-failed-read -cvf results_$timestamp.tar -C $PROJECT_DIR output/ > /dev/null

rm $PROJECT_DIR/output -r
mkdir $PROJECT_DIR/output
mkdir $PROJECT_DIR/output/ec_files
mkdir $PROJECT_DIR/output/screenshots

