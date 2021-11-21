#!/bin/bash

cd $ANDROID_HOME/emulator
sudo nohup ./emulator -avd test -no-window -writable-system -no-cache &
cd $FUZZER/FuzzerEngine/fuzzerengine
