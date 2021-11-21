#!/bin/bash

./shutdown_avd.bash

rm -f *.pyc

rm -f $TESTER/*.pyc
rm -f -r $FUZZER/output
rm -f $FUZZER/aut_apk/*.apk

rm -f $ANDROID_HOME/emulator/nohup.out<<-EOF
yes
EOF
