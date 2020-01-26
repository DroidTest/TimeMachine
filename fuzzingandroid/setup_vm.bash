#!/bin/bash


#
# USAGE: ./setup.sh Android7_1 6000
# To create machine "Android7_1" running with adb port 6000 



MACHINE_NAME=$1
ADB_PORT=$2


load_dkms()
{
          
        MODULE="vboxdrv"
        if lsmod | grep "$MODULE" &> /dev/null ; then
                 echo "$MODULE is loaded!"
        else
                echo "Loading $MODULE !"
                insmod $(find /lib/modules/ -name $MODULE".ko")
                sleep 1
        fi
}

add_vboxpython2_7()
{
        cp ./libs/VBoxPython2_7.so /usr/lib/virtualbox/
}

#setting virtualbox
add_vboxpython2_7
load_dkms


#create a vm with name "Android6"
vboxmanage createvm --name $MACHINE_NAME --ostype Linux26_64 --register

#setting memory
vboxmanage modifyvm $MACHINE_NAME --memory 4096

#setting network model
vboxmanage modifyvm $MACHINE_NAME --nic1 nat

#creating a storage controller
vboxmanage storagectl $MACHINE_NAME --name IDE --add ide


DISK_PATH=~/AndroidOS/Android7.1
ORIGINAL_DISK=$DISK_PATH/Android7.1_base_disk.vmdk
NEW_DISK=$DISK_PATH/$MACHINE_NAME"_disk.vmdk"

#Attaching a guest OS
#vboxmanage storageattach Android6 --storagectl IDE --port 0 --device 0 --type hdd --medium   ~/AndroidOS/Android6/Android-x86-6.0-r3-64bit.vdi

cp $ORIGINAL_DISK $NEW_DISK
vboxmanage internalcommands sethduuid $NEW_DISK

vboxmanage storageattach $MACHINE_NAME --storagectl IDE --port 0 --device 0 --type hdd --medium $NEW_DISK

#forwarding adb port(5555)to localhost:6000
vboxmanage modifyvm $MACHINE_NAME --natpf1 "adb,tcp,127.0.0.1,"$ADB_PORT",,5555"

#forwarding emulator ssh connection port to localhost:2222
#vboxmanage modifyvm $MACHINE_NAME --natpf1 "tcp,tcp,127.0.0.1,"$TCP_PORT",,65000"

#vboxmanage modifyvm $MACHINE_NAME --natpf1 "ssh,tcp,127.0.0.1,"$SSH_PORT",,2222"


