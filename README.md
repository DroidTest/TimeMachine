# TimeMachine <img align="right" src="https://zenodo.org/badge/DOI/10.5281/zenodo.3672076.svg">

TimeMachine is an automated testing tool for Android apps, which can automatically jump to the most progressive state observed in the past when progress is slow. 

<p align="center">
<img src="https://github.com/DroidTest/TimeMachine/blob/master/illustration.jpg" width="600">
</p>

TimeMachine leverages virtualization technology such as emulator to save an app state and restore it when needed. TimeMachine identifies an app state with GUI layout and memories all discovered states. When a state is considered interesting, e.g., new code is covered, TimeMachine saves the state. Meanwhile, TimeMachine observes most-recently-visited states to check whether progress is slow, e.g., being stuck in a state loop. Once progress is considered slow, TimeMachine restores the most progressive one from saved states for further exploration so that more program behavior is exercised in a short time. 

    


<!---
The figure above demonstrates how it works. When execution keeps going through a loop state S2 -- S3 -- S4 -- S2 (see Figure (a)), TimeMachine terminates the current execution due to lack of progress, resumes the most progressive state S1 (assuming that S1 is the most progressive state among all discovered states),  and launches a new execution from state S1. When reaching state S6 via S5 (see Figure(b)), the execution gets stuck, i.e., unable to exit the state after executing a fixed amount of events. TimeMachine terminates current execution again and resumes the most progressive state S5 to launch a new execution. The whole process is automatically triggered during testing.
--->

## Publication ##
The paper PDF can be found at https://zhendong2050.github.io/res/time-travel-testing-21-01-2020.pdf
```
@InProceedings{zhendong:icse:2020,
author = {Dong, Zhen and B{\"o}hme, Marcel and Cojocaru, Lucia and Roychoudhury, Abhik},
title = {Time-travel Testing of Android Apps},
booktitle = {Proceedings of the 42nd International Conference on Software Engineering},
series = {ICSE '20},
year = {2020},
pages={1-12}}

```

## What is new? ##
For easy-to-use, we upgrade TimeMachine with following features:

* Using the Android Emulator instead of Virtualbox 
* Using Jacoco for instrumenting apps instead of EMMA
* Solving other issues such as adb connection issues during fuzzing  


## Prerequisites ##
* Ububntu 18.04 64-bit or Mac-OSX 10.15 
* Android SDK with API 25 (ensuring adb, aapt, avdmanager, emulator correctly configured) 
* Python 2.7 (ensuring enum and uiautomator packages are installed)

<!--
adb, aapt, avdmanager, emulator 
enum, uiautomator

Check commands "adb", "aapt", "avdmanager", "emulator" in your terminal to make sure you have correctly configured environment variables.
Package "enum" and "uiautomator" are needed in python2.7.
-->



## Run TimeMachine ##
### 1. Clone Repos ###
*  Clone TimeMachine
```
# creating workspace
mkdir workspace
cd workspace

git clone https://github.com/DroidTest/TimeMachine.git
```

*  Clone an example app [AmazeFileManager v3.4.2](https://github.com/TeamAmaze/AmazeFileManager/releases/tag/v3.4.2)
```
# creating dir for AUT
mkdir appTest
cd appTest

git clone --branch v3.4.2 https://github.com/TeamAmaze/AmazeFileManager.git
```

### 2. Instrument the app with [Jacoco](https://www.jacoco.org/jacoco/) ###
*  Build an instrumented apk
```
# Add the jacoco plugin
echo -e "\napply plugin: 'jacoco'" >> AmazeFileManager/app/build.gradle
sed -i "`sed -n -e "/debug {/=" AmazeFileManager/app/build.gradle` a testCoverageEnabled true" AmazeFileManager/app/build.gradle

cp -r ../TimeMachine/JacocoIntegration/JacocoInstrument AmazeFileManager/app/src/main/java/com/amaze/filemanager

# Add package names
sed -i '1i package com.amaze.filemanager.JacocoInstrument;' AmazeFileManager/app/src/main/java/com/amaze/filemanager/JacocoInstrument/FinishListener.java
sed -i '1i package com.amaze.filemanager.JacocoInstrument;' AmazeFileManager/app/src/main/java/com/amaze/filemanager/JacocoInstrument/JacocoInstrumentation.java
sed -i '1i package com.amaze.filemanager.JacocoInstrument;' AmazeFileManager/app/src/main/java/com/amaze/filemanager/JacocoInstrument/SMSInstrumentedReceiver.java

# Register the BroadcastReceiver in AndroidManifest.xml
sed -i "`sed -n -e "/<\/application>/=" AmazeFileManager/app/src/main/AndroidManifest.xml` i <receiver android:name=\".JacocoInstrument.SMSInstrumentedReceiver\"><intent-filter><action android:name=\"edu.gatech.m3.emma.COLLECT_COVERAGE\"/></intent-filter></receiver>" AmazeFileManager/app/src/main/AndroidManifest.xml

# Build app with gradle
cd AmazeFileManager

./gradlew clean
./gradlew --no-daemon assembleDebug
```

*  Setup the apk in the test folder

```
cp app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk ../AmazeFileManager.apk

# Generate a class_files.json to describe the built directory
echo "{\"AmazeFileManager.apk\": {\"classfiles\": [\"AmazeFileManager/app/build/intermediates/javac/fdroidDebug/classes/\",\"AmazeFileManager/commons_compress_7z/build/intermediates/javac/debug/classes/\"]}}" > ../class_files.json
```



### 3. Check if the instrumented app works ###
```
# Launch emulator
sdkmanager "system-images;android-25;google_apis;x86"
avdmanager create avd -n avd0 -k "system-images;android-25;google_apis;x86" -d pixel_2_xl -c 1000M -f
nohup emulator -avd avd0 -writable-system &
adb devices
adb wait-for-device
adb root

# Run the apk on emulator 
adb install -g ../AmazeFileManager.apk
adb shell am start com.amaze.filemanager.debug/com.amaze.filemanager.activities.MainActivity

# Check if the coverage.ec file is generated. If so, the apk works well. 
adb shell am broadcast -a edu.gatech.m3.emma.COLLECT_COVERAGE
adb shell "cat /data/data/com.amaze.filemanager.debug/files/coverage.ec" 
adb emu kill
```


### 4. Test the apk with TimeMachine ###
* Launch TimeMachine
```  
cd ../../TimeMachine/fuzzingandroid

python2.7 main.py --avd avd0 --apk ../../appTest/AmazeFileManager.apk --time 1h -o ../../appTest/timemachine-results --no-headless
```   
* Check testing results

```  
ls ../../appTest/timemachine-results/[output_dir_name]

# Expected results:
├── coverage.xml                current jacoco coverage report in xml
├── crashes.log                 crash logs in testing
├── data.csv                    coverage data in csv
├── ec_files                    dirs of *.ec generated by jacoco-agent
│   └── *.ec         
├── run_time.log                time of starting test
└── timemachine-run.log         runtime log of timemachine
```  

<!--
## Usage of TimeMachine ##
```

python2.7 main.py [-h] [--avd AVD_NAME] [--apk APK] [-n NUMBER_OF_DEVICES]
                                [--apk-list APK_LIST] -o O [--time TIME] [--repeat REPEAT]
                                [--no-headless] [--offset OFFSET]


  -h, --help                    show this help message and exit
  --avd AVD_NAME                the device name
  --apk APK                     the path of apk under test
  -n NUMBER_OF_DEVICES          number of emulators created for testing, default: 1
  --apk-list APK_LIST           list of apks under test
  -o O                          output dir
  --time TIME                   the fuzzing time in hours (e.g., 6h), minutes (e.g.,
                                6m), or seconds (e.g., 6s), default: 6h
  --repeat REPEAT               the repeated number of runs, default: 1
  --no-headless                 show gui or not
  --offset OFFSET               device offset number w.r.t emulator-5554
```  


Useful scripts:
```
#check current jacoco line coverage
python2.7 compute_coverage.py ../../appTest/timemachine-results/[output_dir_name]

#Check crashes
cat ../../appTest/timemachine-results/[output_dir_name]/crashes.log

#Check logs
cat ../../appTest/timemachine-results/[output_dir_name]/timemachine-run.log
```

### Changes from TimeMachine 1.0
* remove Virtualbox from architecture to perform better
* replace coverage collection tool from Emma to Jacoco
* testing of closed source projects instrumented by ella is no longer supported
-->



