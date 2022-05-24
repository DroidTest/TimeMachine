#!/bin/sh

#判断参数是否完整
if [ ! -n "$1" ]
then
    echo "Argument missing, try './take_snapshot.sh <shot_name> <avdport>'"
    exit
fi

shot_name=$1 #快照名称

avdport=$2 #avd运行端口
auth_token=$(cat ~/.emulator_console_auth_token) #验证口令

takesnapshot(){
    /usr/bin/expect<<-EOF
    spawn telnet localhost $avdport
    expect "OK" { send "auth $auth_token\n" }
    expect "OK" { send "avd snapshot save $shot_name\n"}
    expect "OK" { send "quit\n"}
    expect eof
EOF
}

takesnapshot
