#!/bin/bash

# This script describes process of launching TimeMachine, which consists of 3 steps:
# 1. Setting a vm with Android7
# 2. Configging and intalling app under test
# 3. Launching test engine
# The script takes 3 parameters
# First parameter: the folder name of app under in /home/hypermonkey/subjects. The folder name uniquely identifies an app
# Second parameter: the total testing time
# Third parameter: assign a label to the execution and identifies results from multiple excutions

# usage ./start.sh /home/hypermonkey/subjects/org.waxworlds.edam.importcontacts_2_src/bin 3 21600


function wait_adb {
    while true; do
        echo 'adb not connected!'
        adb disconnect $DEVICE > /dev/null 2>&1
        adb connect $DEVICE > /dev/null 2>&1

        ! timeout 2 adb -s $DEVICE wait-for-device shell exit 0 > /dev/null 2>&1 || break
    done 
    
    adb -s $DEVICE wait-for-device
}

# Step 1:
# Setting a VM by calling a script setup_vm.sh under home directory
# First parameter: the name of VM, Android7_1 by default
# Second parameter: redirect 5555 port of vm to port 6000 of hosting machine
# Third parameter: port is used by the SSH server on Android OS, 2222 by default

APP_DIR=${1}
HEADLESS=${2}
TIMEOUT=${3}
VM=${4:-'Android7_1'}

if (( $# < 3 )); then
    echo 'Wrong usage!'
    exit 1
fi

./setup_vm.bash $VM 6000 


# Step 2:
# Specifying app under test from benchmark by the folder name

APP_APK=$APP_DIR/instrumented.apk  # multiple apks under folder and *.apk is used

# extracting the package name and the activity name
APP_PKG=`aapt dump badging $APP_APK | grep package | awk '{print $2}' | sed s/name=//g | sed s/\'//g`
APP_ACT=`aapt dump badging $APP_APK | grep launchable-activity | awk '{print $2}' | sed s/name=//g | sed s/\'//g`

echo "package name: $APP_PKG"
# copy apk, .em (for coverage compuation) files to specified folders
cp $APP_APK ~/fuzzingandroid/aut_apk/aut.apk
cp $APP_DIR/coverage.em  ~/fuzzingandroid/emma_jars/


#stop the vm, in case it is running
VBoxManage controlvm $VM poweroff

# launch VM with headless modle 
STR_Startvm="VBoxManage startvm $VM"

(( $HEADLESS == 1 )) && STR_Startvm="$STR_Startvm --type headless"

$STR_Startvm

#connect device with adb
DEVICE="127.0.0.1:6000"

wait_adb

# Make the IP static to fast recovery after restoring
#adb -s $DEVICE shell 'su -c "(ifconfig eth0 down && ifconfig eth0 up `echo $(ip addr show dev eth0 | grep inet | head -n1) | cut -d\" \" -f2` ) &; exit"; exit'

wait_adb

# uninstall the app if installed, then install
echo "uninstall app in case it is installed before"
adb -s $DEVICE uninstall $APP_PKG

echo "install app under test"
# Give the app all the permissions when installing
adb -s $DEVICE install -g ~/fuzzingandroid/aut_apk/aut.apk 

# pressing permission button for installation
sleep 5
#adb -s $DEVICE shell reboot -p
#adb -s $DEVICE shell input tap 580 350
#echo "Tapped"

echo "shut down VM after installing app"
VBoxManage controlvm $VM poweroff



cd FuzzerEngine/fuzzerengine
Mode="gui"
(( $HEADLESS == 1 )) && Mode="headless"
./executor.py $VM 6000 2222 "$APP_PKG" $TIMEOUT 1 pareto 0 $Mode


