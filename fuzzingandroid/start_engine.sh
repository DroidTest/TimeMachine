#!/bin/sh

APP_DIR=$1

TIMEOUT=$2

OUTPUT_DIR=$3

APK_FILE_NAME=`basename $4`

AVD_SERIAL=$5

AVD_PORT=$6

AVD_NAME=$7


echo "AVD_SERIAL: "$AVD_SERIAL
echo "AVD_PORT: "$AVD_PORT

wait_adb(){
    while true;
    do
        echo 'adb not connected!'
        adb -s $AVD_SERIAL devices > /dev/null 2>&1
        ! timeout 3 adb -s $AVD_SERIAL wait-for-device shell exit 0 > /dev/null 2>&1 || break
    done 
    
    adb -s $AVD_SERIAL wait-for-device
}

replace_monkey_uiautomator(){
	adb -s $AVD_SERIAL root
	wait_adb
	adb -s $AVD_SERIAL remount
	adb -s $AVD_SERIAL push libs/monkey /system/bin/
	adb -s $AVD_SERIAL push libs/monkey.jar /system/framework/
		adb -s $AVD_SERIAL push libs/uiautomator.jar /system/framework/
	adb -s $AVD_SERIAL shell chmod 777 /system/framework/monkey.jar
	adb -s $AVD_SERIAL shell chmod 777 /system/framework/uiautomator.jar
	adb -s $AVD_SERIAL shell chmod 777 /system/bin/monkey
}

#launch avd
#sudo nohup ./~/Android/sdk/emulator/emulator -avd test -no-window -writable-system -no-qt -no-cache &

wait_adb
echo "adb connect successfully"

replace_monkey_uiautomator

sleep 5

adb -s $AVD_SERIAL uninstall com.github.uiautomator > /dev/null 2>&1
adb -s $AVD_SERIAL uninstall com.github.uiautomator.test > /dev/null 2>&1
sleep 3
adb -s $AVD_SERIAL install libs/app-uiautomator.apk > /dev/null 2>&1
adb -s $AVD_SERIAL install libs/app-uiautomator-test.apk > /dev/null 2>&1

APP_APK=$APP_DIR/$APK_FILE_NAME
echo "APP_APK:" $APP_APK

APP_PKG=`aapt dump badging $APP_APK | grep package | awk '{print $2}' | sed s/name=//g | sed s/\'//g`
echo "package name: "$APP_PKG

echo "uninstall app in case it is installed before"
adb -s $AVD_SERIAL uninstall $APP_PKG > /dev/null 2>&1
echo "uninstall finished"

echo "install app under test"
# Give the app all the permissions when installing
adb -s $AVD_SERIAL install -g $APP_APK
# pressing permission button for installation
sleep 5

echo "OUTPUT_DIR: "$OUTPUT_DIR
rm -rf $OUTPUT_DIR
mkdir $OUTPUT_DIR
mkdir $OUTPUT_DIR/temp_ec
mkdir $OUTPUT_DIR/ec_files
touch $OUTPUT_DIR/timemachine-run.log
OUTPUT_LOG_PATH=$OUTPUT_DIR/timemachine-run.log

echo "bash executed successfully! start engine now ..."
cd FuzzerEngine/fuzzerengine
echo "python2.7 executor.py $APP_DIR $APP_PKG $APK_FILE_NAME $TIMEOUT $OUTPUT_DIR $AVD_SERIAL $AVD_PORT $AVD_NAME | tee -a $OUTPUT_LOG_PATH"
python2.7 executor.py $APP_DIR $APP_PKG $APK_FILE_NAME $TIMEOUT $OUTPUT_DIR $AVD_SERIAL $AVD_PORT $AVD_NAME | tee -a $OUTPUT_LOG_PATH
