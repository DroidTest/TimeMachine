#!/bin/bash

# When fuzzing via TimeMachine, the aut's classfiles are stored under the same directory of the apk

APK_FILE=$1 # e.g., xx.apk
AVD_SERIAL=$2 # e.g., emulator-5554
AVD_NAME=$3 # e.g., base
OUTPUT_DIR=$4
TEST_TIME=$5 # e.g., 10s, 10m, 10h
HEADLESS=$6 # e.g., -no-window
ADB_PORT=$7

echo "----"
echo "ADB_PORT: ${ADB_PORT}"
echo "----"

echo $HEADLESS

# check machine name to enable run TimeMachine on ETH/machines
host_machine_name=`hostname`

AVD_PORT=${AVD_SERIAL:9:13}

# wait for the target device
function wait_for_device(){
    avd_serial=$1
    timeout 5s adb -s $avd_serial wait-for-device
    OUT=`adb -s $avd_serial shell getprop init.svc.bootanim`
    i=0
    while [[ ${OUT:0:7}  != 'stopped' ]]; do
      echo "   Waiting for emulator (${avd_serial}) to fully boot (#${i} times) ..."
      sleep 5
      i=$(expr $i + 1)
      if [[ $i == 10 ]]
      then
            echo "Cannot connect to the device: (${avd_serial}) after (#${i} times)..."
            break
      fi
      OUT=`adb -s $avd_serial shell getprop init.svc.bootanim`
    done
}


echo "clean cache for emulator ..."
emulator -port $AVD_PORT -avd $AVD_NAME -writable-system -no-window -no-cache > /dev/null 2>&1 &
# wait for the emulator
wait_for_device $AVD_SERIAL > /dev/null 2>&1
adb -s $AVD_SERIAL emu kill > /dev/null 2>&1

RETRY_TIMES=5
for i in $(seq 1 $RETRY_TIMES);
do
    echo "try to start the emulator (${AVD_SERIAL})..."
    sleep 5
    # start the emulator
    emulator -port $AVD_PORT -avd $AVD_NAME -writable-system $HEADLESS &
    sleep 5
    # wait for the emulator
    wait_for_device $AVD_SERIAL

    # check whether the emualtor is online
    OUT=`adb -s $avd_serial shell getprop init.svc.bootanim`
    if [[ ${OUT:0:7}  != 'stopped' ]]
    then
        adb -s $avd_serial emu kill
        echo "try to restart the emulator (${AVD_SERIAL})..."
        if [[ $i == RETRY_TIMES ]]
        then
            echo "we give up the emulator (${AVD_SERIAL})..."
            exit
        fi
    else
        break
    fi
done

echo "  emulator (${AVD_SERIAL}) is booted!"
adb -s ${AVD_SERIAL} root



current_date_time="`date "+%Y-%m-%d-%H-%M-%S"`"
apk_file_name=`basename $APK_FILE`
apk_file_path=`realpath $APK_FILE`
apk_dir_path=`dirname $apk_file_path`
result_dir=`realpath $OUTPUT_DIR/$apk_file_name.timemachine.result.$AVD_SERIAL\#$current_date_time`
mkdir -p $result_dir
echo "** CREATING RESULT DIR (${AVD_SERIAL}): " $result_dir

# get app package
app_package_name=`aapt dump badging $APK_FILE | grep package | awk '{print $2}' | sed s/name=//g | sed s/\'//g`
echo "** PROCESSING APP (${AVD_SERIAL}): " $app_package_name

# run TimeMachine
# jump into TimeMachine's exec folder
echo "** RUN TIMEMACHINE "
echo "`date "+%Y-%m-%d-%H:%M:%S"`" >> $result_dir/timemachine_testing_time.txt
cmd="./start_engine.sh $apk_dir_path $TEST_TIME $result_dir $apk_file_path $AVD_SERIAL $AVD_PORT $AVD_NAME"
echo $cmd
timeout 12h ./start_engine.sh $apk_dir_path $TEST_TIME $result_dir $apk_file_path $AVD_SERIAL $AVD_PORT $AVD_NAME
echo "`date "+%Y-%m-%d-%H:%M:%S"`" >> $result_dir/timemachine_testing_time.txt

sleep 5
adb -s $AVD_SERIAL emu kill
echo "@@@@@@ Finish (${AVD_SERIAL}): " $app_package_name "@@@@@@@"
