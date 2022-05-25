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
The paper PDF can be found at https://www.comp.nus.edu.sg/~abhik/pdf/ICSE20TM.pdf
```
@InProceedings{zhendong:icse:2020,
author = {Dong, Zhen and B{\"o}hme, Marcel and Cojocaru, Lucia and Roychoudhury, Abhik},
title = {Time-travel Testing of Android Apps},
booktitle = {Proceedings of the 42nd International Conference on Software Engineering},
series = {ICSE '20},
year = {2020},
pages={1-12}}

```

## Envrionment Setup ##
TimeMachine runs on a Unix-like operating system. 
The following is required to set up TimeMachine:
* Android SDK with API 25
* Python 2.7
* Ububntu 18.04 64-bit or Mac-OSX 10.15 

### Android SDK installation ###
Run the following command to download the latest Android cmdline-tools:
```
wget https://dl.google.com/android/repository/commandlinetools-linux-6858069_latest.zip
``` 

The following SDK components are necessary: 
* android-25 platforms
* emulator
* platform-tools
* build-tools

Check commands "adb", "aapt", "avdmanager", "emulator" in your terminal to make sure you have correctly configured environment variables.

### Emulator starting ###
Create an avd by avdmanager for testing:
```
avdmanager create avd -n avd0 -k "system-images;android-25;google_apis;x86"<<EOF
n
EOF
```
Now you can use command "emulator" to start an Android emulator:
```
emulator -avd avd0 -writable-system 
```
### Python 2.7 ###
For Ubuntu, run following commands:
```
sudo apt install expect python2.7 python-pip
pip install enum uiautomator
```
## App instrumentation ##
TimeMachine takes as input open-source apps instrumented with [Jacoco](https://www.jacoco.org/jacoco/). 
Under folder instrumented_apps are several open-source apps instrumented with Jacoco, i.e., AmazeFileManager and FirefoxLite. 
You can also use TimeMachine to test your own jacoco-integrated apps.

### directory for app under testing ###
A directory of app under testing should contain the following subjects.
```
├── source code                   jacoco-integrated source codes of app with built directories compiled by javac
├── class_files.json              json file for describing the path to built directory
└── *.apk                         jacoco-instrumented apk file of app under testing
```

### Jacoco integration example ###
You can search [Jacoco Offline Instrumentation](https://www.jacoco.org/jacoco/trunk/doc/offline.html) to integrate your own open-source apps with Jacoco offline mode. 
Here we take [AmazeFileManager](https://www.jacoco.org/jacoco/) as an example. Usually we only need three steps as below.

First, add the gradle plugin "jacoco" to build.gradle of app module. 
```
apply plugin: 'jacoco'
```
Second, add JacocoInstrument directory we provide to source code directory.
```
JacocoInstrument
├── FinishListener.java          
├── JacocoInstrumentation.java         
└── SMSInstrumentedReceiver.java     
```
Third, regist the BroadcastReceiver we added in AndroidManifest.xml
```
<manifest>
    <application>
        <receiver android:name=".JacocoInstrument.SMSInstrumentedReceiver">
            <intent-filter>
                <action android:name="edu.gatech.m3.emma.COLLECT_COVERAGE" />
            </intent-filter>
        </receiver>
    </application>
</manifest>

```
Now Jacoco integration is finished. We can build output directories and package source codes into apk files. 

### check ###
To check if Jacoco agent works well in apk file, install and run the apk file on the emulator and send broadcast as follows:
 ```
adb shell am broadcast -a edu.gatech.m3.emma.COLLECT_COVERAGE
```
If coverage.ec file is generated under path /data/data/${APP_PACKAGE_NAME}/files, then congratulations.

## Usage ##
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


TimeMachine can be started easily with two steps.
### Step 1: clone repository ###
```
git clone https://github.com/DroidTest/TimeMachine.git
```

### Step 2: start TimeMachine ###
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


