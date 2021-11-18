#!/bin/bash


MODULE="vboxdrv"
load_dkms()
{
          
        if lsmod | grep "$MODULE" &> /dev/null ; then
                 echo "$MODULE is loaded!"
        else
                echo "Loading $MODULE !"
                insmod $(find /lib/modules/ -name $MODULE".ko")
                sleep 1
        fi
}

load_dkms
lsmod | grep "$MODULE"

