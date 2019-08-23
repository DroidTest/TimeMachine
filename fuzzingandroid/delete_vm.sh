#!/bin/bash

# usage ./delete_vm.sh Android7_1
vboxmanage controlvm $1 poweroff
vboxmanage unregistervm $1
cd ~/VirtualBox\ VMs/
rm -r $1/
cd ~/AndroidOS/Android7.1
rm $1_disk.vmdk

cd ~/fuzzingandroid
rm ./output/ec_files/cover*.ec
rm ./output/ella_files/cov
rm ./output/ella_files/coverage.a
