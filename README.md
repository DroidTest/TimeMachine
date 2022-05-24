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

## Architecture ##
The whole system runs in the Ubuntu operating system. App under test is installed in an Android virtual machine. TimeMachine connects the virtual machine via ADB to test the app. Main Components are configured as followed:

* Android SDK
* Android-x86-7.1.r2
* Python 2.7.17

## Todo ##
Optimize methods for state selection to achieve a better performance on coverage. <br>

## Setup ##
The following is required to set up TimeMachine:
* at least 100 GB hard drive 
* 8 GB memory
* Ububntu 18.04 64-bit or Mac-OSX 10.15 

### Step 1: clone repository ###
```
git clone https://github.com/DroidTest/TimeMachine.git
```
### Step 2: install Android sdk ###

You can run the following command to download the latest Android cmdline-tools:
```
wget https://dl.google.com/android/repository/commandlinetools-linux-6858069_latest.zip
``` 
Unzip the zip package and copy all files under directory "cmdline-tools" to the path " ~ / Android / sdk / cmdline-tools / latest ".<br> 
Then you can use the sdkmanager under directory "bin" to install apis, emulator, etc.

```
#install android-25, emulator, platform-tools, build-tools
./sdkmanager --update
./sdkmanager "system-images;android-25;google_apis;x86" platform-tools "platforms;android-25" "build-tools;29.0.0"<<EOF
y
EOF
``` 
**Note1:** If the download or installation command fails, please check if you are permitted to visit the google server first.<br>
**Note2:** If you are running TimeMachine on Mac-OSX, please make sure the latest version of emulator fits your OS well, or you will have to run with old emulator by following this: https://stackoverflow.com/questions/66455173/android-emulator-30-4-5-not-working-on-macos


### step 3: install dependencies ###
TimeMachine use Python 2.7.17 as interpreter. 

If you are running TimeMachine on the Ubuntu 18.04, just run the following commands:
```
sudo apt install expect python2.7 python-pip
pip install enum uiautomator
```
However, there may be compilation errors if you directly use the built-in Python 2.7 provided by Mac-OSX 10.15. In this case, we recommend an external Python 2.7 environment which could be installed by conda(https://docs.conda.io) or other available package managers.

### Step 4: configure environment variables ###

Please make sure you have correctly configured the following environment variables in your property files. 
```
export ANDROID_HOME="~/Android/sdk"
export ANDROID_TOOLS=$ANDROID_HOME/cmdline-tools/latest/bin
export ADB_HOME="$ANDROID_HOME/platform-tools"
export AAPT_HOME="$ANDROID_HOME/build-tools/29.0.0"

export PATH=$ANDROID_TOOLS:$ADB_HOME:$AAPT_HOME:$PATH
```
Check the correct configured environment by typing command "adb", "aapt", "avdmanager" in your terminal.

### step 5: create an avd by avdmanager ###
```
avdmanager create avd -n avd0 -k "system-images;android-25;google_apis;x86"<<EOF
n
EOF
```
## Usage ##
TimeMachine takes as input apks instrumented with Android apps instrumenting tool [Jacoco](https://www.jacoco.org/jacoco/). Under folder instrumented_apps are several open-source apps instrumented with Jacoco, i.e., AmazeFileManager and FirefoxLite. The command lines for deployment are as follow.
```
usage: python2.7 main.py [-h] [--avd AVD_NAME] [--apk APK] [-n NUMBER_OF_DEVICES]
                         [--apk-list APK_LIST] -o O [--time TIME] [--repeat REPEAT]
                         [--no-headless] [--offset OFFSET]

optional arguments:
  -h, --help            show this help message and exit
  --avd AVD_NAME        the device name
  --apk APK
  -n NUMBER_OF_DEVICES  number of emulators created for testing, default: 1
  --apk-list APK_LIST   list of apks under test
  -o O                  output dir
  --time TIME           the fuzzing time in hours (e.g., 6h), minutes (e.g.,
                        6m), or seconds (e.g., 6s), default: 6h
  --repeat REPEAT       the repeated number of runs, default: 1
  --no-headless         show gui
  --offset OFFSET       device offset number w.r.t emulator-5554

```  
Test example apps by the following scripts:
```  
cd TimeMachine/fuzzingandroid

python2.7 main.py --avd avd0 --apk ../instrumented_apps/AmazeFileManager/AmazeFileManager-3.4.2-#1837.apk --time 1h -o ../timemachine-results --no-headless

```  


## Output ##
check current jacoco line coverage
```
python2.7 compute_coverage.py ../timemachine-results/[output_file_dir_name]
```
check crashes
```
cat ../timemachine-results/[output_file_dir_name]/crashes.log
```
check logs
```
cat ../timemachine-results/[output_file_dir_name]/timemachine-run.log
```
## Need help? ##
* Contact Zhen Dong for further issues.
## Contributors ##
* Zhen Dong (zhendng@gmail.com)
* Lucia Cojocaru
* Xiao Liang Yu
* Marcel Böhme
* Abhik Roychoudhury
* CAI Xiaobao


