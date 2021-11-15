#!/bin/bash

function wait_adb {
    while true;
    do
        echo 'adb not connected!'
        adb devices > /dev/null 2>&1
        ! timeout 3 adb wait-for-device shell exit 0 > /dev/null 2>&1 || break
    done 
    
    adb wait-for-device
}

replace_monkey_uiautomator(){
	adb root
	wait_adb
	adb remount
	adb push ../../libs/monkey /system/bin/
	adb push ../../libs/*.jar /system/framework/
	adb shell chmod 777 /system/framework/monkey.jar
	adb shell chmod 777 /system/framework/uiautomator.jar
	adb shell chmod 777 /system/bin/monkey
}

#launch avd
#sudo nohup ./~/Android/sdk/emulator/emulator -avd test -no-window -writable-system -no-qt -no-cache &

wait_adb
echo "adb connect successfully"

replace_monkey_uiautomator

adb uninstall com.github.uiautomator
adb uninstall com.github.uiautomator.test
adb install $FUZZER/../app-uiautomator.apk
adb install $FUZZER/../app-uiautomator-test.apk
#get package name of aut
APP_PKG=`aapt dump badging ../../aut_apk/aut.apk | grep package | awk '{print $2}' | sed s/name=//g | sed s/\'//g`
echo $APP_PKG

echo "uninstall app in case it is installed before"
adb uninstall $APP_PKG

echo "install app under test"
# Give the app all the permissions when installing
adb install -g ../../aut_apk/aut.apk
# pressing permission button for installation
#sleep 5

TIMEOUT=1800

OPEN_SOURCE=0

echo "bash executed successfully! start engine now ..."
./executor.py $OPEN_SOURCE $APP_PKG $TIMEOUT
