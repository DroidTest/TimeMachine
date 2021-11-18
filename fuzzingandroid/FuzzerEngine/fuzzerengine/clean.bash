#!/bin/bash

./shutdown_avd.bash

rm -f nohup.out<<-EOF
yes
EOF

rm -f *.pyc

rm -f $TESTER/*.pyc
rm -f -r $FUZZER/output
rm -f $FUZZER/aut_apk/*.apk
