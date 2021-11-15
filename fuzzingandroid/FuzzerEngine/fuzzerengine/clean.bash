#!/bin/bash

./shutdown_avd.bash

rm nohup.out<<-EOF
yes
EOF

rm *.pyc

rm $TESTER/*.pyc
