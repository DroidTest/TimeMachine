# TimeMachine #

TimeMachine is an automated testing tool for Android apps,  which can automatically jump to the most progressive state observed in the past when progress is slow. 

<p align="center">
<img src="https://github.com/DroidTest/TimeMachine/blob/master/illustration.jpg" width="600">
</p>

TimeMachine leverages virtualization technology such as emulator to save an app state and restore it when needed. TimeMachine identifies an app state with GUI layout and memories all discovered states. When a state is considered interesting, e.g., new code is covered, TimeMachine saves the state. Meanwhile, TimeMachine observes most-recently-visited states to check whether progress is slow, e.g., being stuck in a state loop. Once progress is considered slow, TimeMachine restores the most progressive one from saved states for further exploration so that more program behavior is exercised in a short time. 

    


<!---
The figure above demonstrates how it works. When execution keeps going through a loop state S2 -- S3 -- S4 -- S2 (see Figure (a)), TimeMachine terminates the current execution due to lack of progress, resumes the most progressive state S1 (assuming that S1 is the most progressive state among all discovered states),  and launches a new execution from state S1. When reaching state S6 via S5 (see Figure(b)), the execution gets stuck, i.e., unable to exit the state after executing a fixed amount of events. TimeMachine terminates current execution again and resumes the most progressive state S5 to launch a new execution. The whole process is automatically triggered during testing.
--->

## Publication ##
```
@InProceedings{zhendong:icse:2020,
author = {Zhen Dong, Marcel B\"ohme, Lucia Cojocaru, Abhik Roychoudhury},
titile = {Time-travel Testing of Android Apps},
booktitle = {Proceedings of the 42nd International Conference on Software Engineering},
series = {ICSE '20},
year = {2020}
}

```

## Architecture ##
<p align="center">
<img src="https://github.com/DroidTest/TimeMachine/blob/master/arch.jpg" width="600">
</p>

The figure above shows TimeMachine's architecture. The whole system runs in a docker container with the Ubuntu operating system. App under test is installed in an Android virtual machine. TimeMachine connects the virtual machine via ADB to test the app. Main Components are configured as followed:

* Android SDK Version 25  
* Android-x86-7.1.r2
* Virtualbox v5.0.18
* Docker API v1.13 or above 
* Python 2.7.2
## ToDo ##
* To build a component to dynamically load virtualbox-dkms from a container
  - Installations of virtualbox and linux-header can be avoided on the host machine,
  - Only docker installation is needed on the host machine.
## Setup ##
The following is required to set up TimeMachine:
* at least 100 GB hard drive 
* 8 GB memory
* Ububntu 16.04 64-bit

### Step 1: clone repository ###
```
git clone https://github.com/DroidTest/TimeMachine.git
```
### Step 2: install dependencies ###

install a specific version of linux header (using linux-headers-4.4.0-124-generic) 
```
sudo apt-get install linux-headers-generic 
```
install virtualbox 5.0.18
```
sudo apt-get install virtualbox-dkms=5.0.18-dfsg-2ubuntu1
sudo apt-get install virtualbox=5.0.18-dfsg-2build1
sudo apt-get install virtualbox-qt=5.0.18-dfsg-2build1
```
install and configure docker 
```
sudo apt-get install docker.io
sudo groupadd docker
sudo usermod -aG docker $USER
newgrp docker 
```
install aapt
```
sudo apt install aapt
```
### step 3: build an docker image ###
```
./build_docker_image.bash
```
It takes around 20 minutes.
## Usage ##
```
cd fuzzingandroid
```
Test example apps in a container   
```
./exec-single-app.bash ../two_apps_under_test/ms_word/ 21600 ../word_output
./exec-single-app.bash ../two_apps_under_test/duolingo/ 21600 ../duolingo_output
```  
## Output ##
check method coverage
```
./compute_cov_aver.bash ../word_output/ ../two_apps_under_test/ms_word/
./compute_cov_aver.bash ../duolingo_output/ ../two_apps_under_test/duolingo/
```
check crashes
```
cat word_output/hypermonkey-output/pareto_crash.log
cat duolingo_output/hypermonkey-output/pareto_crash.log
```
## Need help? ##
* If failed to connect VM, please check whether the version of installed linux-header is 4.4.0-124-generic
* Contact Zhen Dong for further issues.
## Contributors ##
* Zhen Dong (zhendng@gmail.com)
* Lucia Cojocaru
* Xiao Liang Yu
* Marcel Böhme
* Abhik Roychoudhury


