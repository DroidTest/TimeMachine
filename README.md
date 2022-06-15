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




## Prerequisite ##
* Ububntu 18.04 64-bit or Mac-OSX 10.15 
* Android SDK with API 25 (ensuring adb, aapt, avdmanager, emulator correctly configured) 
* Python 2.7 with enum and uiautomator packages

<!--
adb, aapt, avdmanager, emulator 
enum, uiautomator

Check commands "adb", "aapt", "avdmanager", "emulator" in your terminal to make sure you have correctly configured environment variables.
Package "enum" and "uiautomator" are needed in python2.7.
-->



## Running TimeMachine ##
1. Clone TimeMachine
```
# creating workspace
mkdir appTest
cd appTest

git clone https://github.com/DroidTest/TimeMachine.git
```

2. Clone an example app [AmazeFileManager v3.4.2](https://github.com/TeamAmaze/AmazeFileManager/releases/tag/v3.4.2)
```
git clone --branch v3.4.2 https://github.com/TeamAmaze/AmazeFileManager.git
```
3. Instrumenting app with [Jacoco](https://www.jacoco.org/jacoco/)
```
# Add the jacoco plugin
echo -e "\napply plugin: 'jacoco'" >> AmazeFileManager/app/build.gradle
cp -r TimeMachine/JacocoIntegration/JacocoInstrument AmazeFileManager/app/src/main/java/com/amaze/filemanager

# Add package names
sed -i '1i package com.amaze.filemanager.JacocoInstrument;' AmazeFileManager/app/src/main/java/com/amaze/filemanager/JacocoInstrument/FinishListener.java
sed -i '1i package com.amaze.filemanager.JacocoInstrument;' AmazeFileManager/app/src/main/java/com/amaze/filemanager/JacocoInstrument/JacocoInstrumentation.java
sed -i '1i package com.amaze.filemanager.JacocoInstrument;' AmazeFileManager/app/src/main/java/com/amaze/filemanager/JacocoInstrument/SMSInstrumentedReceiver.java

# Register the BroadcastReceiver in AndroidManifest.xml
sed -i "`sed -n -e "/<\/application>/=" AmazeFileManager/app/src/main/AndroidManifest.xml` i <receiver android:name=\".JacocoInstrument.SMSInstrumentedReceiver\"><intent-filter><action android:name=\"edu.gatech.m3.emma.COLLECT_COVERAGE\"/></intent-filter></receiver>" AmazeFileManager/app/src/main/AndroidManifest.xml
```
4. Build an instrumented apk
```
cd AmazeFileManager

./gradlew clean
./gradlew build
```
5. Check if the instrumented app works
```
# Launch emulator

# Run the apk on emulator 
# corret the path of the apk file
adb install -g AmazeFileManager.apk
adb shell am start com.amaze.filemanager.debug/com.amaze.filemanager.activities.MainActivity
adb shell am broadcast -a edu.gatech.m3.emma.COLLECT_COVERAGE

# and check if coverage data is generated
```

If coverage.ec file is generated under path /data/data/${APP_PACKAGE_NAME}/files, then congratulations.


### Step 4: construct directory ###
A directory of app under testing should contain the following subjects.
```
├── AmazeFileManager              jacoco-integrated source codes of app with built directories compiled by javac
├── AmazeFileManager.apk          jacoco-instrumented apk file of app under testing
└── class_files.json              json file for describing the path to built directory
```
In the directory, the class_files.json describes the path to built directory, which should be as follow in this example:
```
{
	"AmazeFileManager.apk": {
		"classfiles": ["AmazeFileManager/app/build/intermediates/javac/fdroidDebug/classes/",
						"AmazeFileManager/commons_compress_7z/build/intermediates/javac/debug/classes/"
		],
		"sourcefiles": ["AmazeFileManager/app/src/main/java/",
						"AmazeFileManager/commons_compress_7z/src/main/java/"
		]
	}
}
```

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


Test example apps by following commands:
```  
cd TimeMachine/fuzzingandroid

python2.7 main.py --avd avd0 --apk ../instrumented_apps/AmazeFileManager/AmazeFileManager-3.4.2-#1837.apk --time 1h -o ../timemachine-results --no-headless
```  

## Output ##
TimeMachine automatedly creates output directories under your specify output path. Current date and emulator serials are used for naming output directories as a destinction.
### output directory ###
An output directory of TimeMachine contains the following subjects:
```
├── coverage.xml                current jacoco coverage report in xml
├── crashes.log                 crash logs in testing
├── data.csv                    coverage data in csv
├── ec_files                    dirs of *.ec generated by jacoco-agent
│   └── *.ec         
├── run_time.log                time of starting test
└── timemachine-run.log         runtime log of timemachine
```

### useful scripts ###
```
#check current jacoco line coverage
python2.7 compute_coverage.py ../timemachine-results/[output_file_dir_name]

#check crashes
cat ../timemachine-results/[output_file_dir_name]/crashes.log

check logs
cat ../timemachine-results/[output_file_dir_name]/timemachine-run.log
```

### Changes from TimeMachine 1.0
* remove Virtualbox from architecture to perform better
* replace coverage collection tool from Emma to Jacoco
* testing of closed source projects instrumented by ella is no longer supported


## Todo ##
Optimize methods for state selection to achieve a better performance on coverage. <br>

## Need help? ##
* Contact Zhen Dong for further issues.

## Contributors ##
* Zhen Dong (zhendng@gmail.com)
* Lucia Cojocaru
* Xiao Liang Yu
* Marcel Böhme
* Abhik Roychoudhury
* CAI Xiaobao


