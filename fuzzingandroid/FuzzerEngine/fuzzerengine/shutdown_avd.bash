#!/bin/bash

avdport=5554 #avd运行端口
auth_token=$(sudo cat ~/.emulator_console_auth_token) #验证口令

shutdown_avd(){
    /usr/bin/expect<<-EOF
    spawn telnet localhost $avdport
    expect "OK" { send "auth $auth_token\n" }
    expect "OK" { send "kill\n"}
    expect "OK" { send "quit\n"}
    expect eof
EOF
}

shutdown_avd