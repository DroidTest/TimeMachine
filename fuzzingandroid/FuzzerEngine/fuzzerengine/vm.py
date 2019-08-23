#!/usr/bin/python
import virtualbox
import time
import subprocess
import os

class VM:
    machine_name = None
    adb_port = None
    ssh_port = None
    ip = '127.0.0.1'
    mode = 'headless' # or "gui"

    def __init__(self, mode, adb_port, ssh_port):
        self.vm = virtualbox.VirtualBox().find_machine(self.machine_name)
        self.session = virtualbox.Session()
        self.mode = mode
        self.adb_port = adb_port
        self.ssh_port = ssh_port

    def launchVM(self):
        if str(self.vm.state) == "PoweredOff" or str(self.vm.state) == "Aborted":
            self.vm.launch_vm_process(self.session, self.mode, '').wait_for_completion(60*1000)
        else:
            raise RuntimeError('The machine is busy')

    def power_off_VM(self):
        try:
            p = self.session.console.power_down()
            p.wait_for_completion(60 * 1000)

        except AttributeError:
            print("The vm is not running")

    def unlock_machine(self, session):
        session.unlock_machine()
        self.wait_unlock_machine(session)

    def wait_unlock_machine(self, session):
        maxTimeout = 60
        while session.state != virtualbox.library.SessionState.unlocked and maxTimeout > 0:
                time.sleep(1)
                maxTimeout -=1


    def take_snapshot(self, snapshot_name, snapshot_description, paused):


        if self.vm.state >= virtualbox.library.MachineState.running:
            p, id_p = self.session.machine.take_snapshot(snapshot_name, snapshot_description, paused)
            p.wait_for_completion(60 * 1000)
        else:
            raise Exception("The machine is not running!")


    def retrieve_snapshot(self,snapshot_id):
        try:
            return self.vm.find_snapshot(snapshot_id)
        except Exception:
            print("This machine does not have any snapshots"+str(snapshot_id)+ ". Instead using the INTIAL state!")
            return self.vm.find_snapshot("INITIAL")
    
    def take_screenshot(self, id):
        os.system('adb -s ' + VM.ip + ':' + VM.adb_port + '  shell screencap /mnt/sdcard/' + str(id) + '.png')
        os.system('adb -s ' + VM.ip + ':' + VM.adb_port + ' pull /mnt/sdcard/' + str(id) + '.png ../../output/screenshots/')
        os.system('adb -s ' + VM.ip + ':' + VM.adb_port + ' shell rm /mnt/sdcard/' + str(id) + '.png')


    def load_snapshot(self, snapshot):

        # if self.vm.state>=virtualbox.library.MachineState.running:
        #     p = session.console.power_down()
        #     p.wait_for_completion(60 * 1000)
        self.wait_unlock_machine(self.session)
        self.vm.lock_machine(self.session, virtualbox.library.LockType.vm)
        snapShotProgress = self.session.machine.restore_snapshot(snapshot)
        snapShotProgress.wait_for_completion(60 * 1000)

        self.unlock_machine(self.session)

        launchVmProgess = self.vm.launch_vm_process(self.session, self.mode, "")
        launchVmProgess.wait_for_completion(60*1000)

    def restore_snapshot(self,uid):

        print("--restore state: " + str(uid))
        # if the machine is running, then power it off
        if self.vm.state >= virtualbox.library.MachineState.running:
            self.power_off_VM()

        time.sleep(1)

        snapshot=self.retrieve_snapshot(uid)
        self.load_snapshot(snapshot)


   
    def restart_adbd_cmd(self):
        subprocess.call('nc -q1 ' + str(self.ip) + ' ' + str(self.tcp_port), shell=True)

    def connect_adb(self):
        subprocess.call("adb connect "+self.ip+":"+str(self.adb_port),shell=True )


    def disconnect_adb(self):
        subprocess.call("adb disconnect "+self.ip+":"+str(self.adb_port),shell=True )
    
    def reconnect_adb(self):
        self.check_connect_adb()
        self.adb_wait_device()

    def check_connect_adb(self):
    
        while True:
            self.connect_adb()
            if 0 == subprocess.call('timeout 2 adb -s '+ VM.ip+':'+VM.adb_port+'  wait-for-device shell exit', shell=True):
                break
            self.disconnect_adb()

        print('adb is connected!')

    def adb_wait_device(self):
        subprocess.check_call('timeout 2 adb -s '+ VM.ip+':'+VM.adb_port+'  wait-for-device', shell=True)

       
        
import fuzzers
import threading
import vm

# test cases
if __name__ == "__main__":

    vm.VM.machine_name = 'Android7_1'
    vm.VM.machine_name = 'Android7_1'
    vm.VM.adb_port = str(6000)
    vm.VM.tcp_port = str(6600)
    vm.VM.tcp_port = str(6600)
    snapshotManager = vm.VM()

    #snapshotManager.power_off_VM()
    #snapshotManager.connect_adb_with_rebooting()

    snapshotManager.launchVM()
    snapshotManager.check_connect_adb()

    mc = fuzzers.MonkeyController()
    monkey_watcher = threading.Thread(target=mc.run_monkey, args=("caldwell.ben.bites", 999999, 1000))
    monkey_watcher.start()

    mc.freeze_monkey()
    snapshotManager.take_snapshot('s3',"",True)

    snapshotManager.restore_snapshot("s3")





