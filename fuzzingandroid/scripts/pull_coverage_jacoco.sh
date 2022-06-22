#!/bin/sh

COV_FILE_NAME=$1
APP_PACKAGE_NAME=$2
CLASS_FILES=$3
AVD_SERIAL=$4
OUTPUT_DIR=$5

EC_DIR=$OUTPUT_DIR/ec_files

JACOCO_PATH=`realpath ../../libs/jacococli.jar`

# Note: jacoco does not know the "~" symbol in file path, so just use the absolute path
#CLASS_FILES=/root/fuzzingandroid/apps/tasks/app/build/intermediates/javac/amazonDebug/classes/

adb -s $AVD_SERIAL shell am broadcast -a edu.gatech.m3.emma.COLLECT_COVERAGE
#adb -s $AVD_SERIAL shell 'su -c "mv /data/data/org.tasks.debug/files/coverage.ec /sdcard/"'
echo "---"
echo "[COVERAGE FILE EXIST?]"
adb -s $AVD_SERIAL shell ls /data/data/${APP_PACKAGE_NAME}/files
echo "---"
adb -s $AVD_SERIAL shell mv /data/data/${APP_PACKAGE_NAME}/files/coverage.ec /sdcard

current_date_time="`date "+%Y-%m-%d-%H-%M-%S"`"
ec_file_name=$AVD_SERIAL`echo $current_date_time`.ec
adb -s $AVD_SERIAL pull /sdcard/coverage.ec $OUTPUT_DIR/ec_files/$ec_file_name

cmd="java -jar $JACOCO_PATH report $OUTPUT_DIR/ec_files/$ec_file_name $CLASS_FILES &> temp"
echo "---"
echo "[VALIDATE COVERAGE FILE]$ $cmd"
echo "---"

# java -jar ~/Projs/app-coverage-analysis/DroidMutator/droidbot/resources/jacococli.jar report coverage.ec --classfiles ./app/build/intermediates/javac/amazonDebug/classes/ --xml tasks.coverage.xml
java -jar $JACOCO_PATH report $OUTPUT_DIR/ec_files/$ec_file_name $CLASS_FILES &> temp

echo "---"
echo "[VALIDATE MESSAGE]"
cat temp
echo "---"


if grep -q -i "Exception" temp
then
    echo "ERROR - removing invalid coverage.ec"
    rm coverage.ec
    adb -s $AVD_SERIAL shell \""rm /data/data/${APP_PACKAGE_NAME}/files/coverage.ec\""''
else
    echo "SUCCESS: coverage file is valid!"
    mkdir -p "${EC_DIR}"
    var=`date +"%T"`
    cp $OUTPUT_DIR/ec_files/$ec_file_name $OUTPUT_DIR/coverage_$var.ec
    cp $OUTPUT_DIR/ec_files/$ec_file_name $EC_DIR/$COV_FILE_NAME.ec
fi
