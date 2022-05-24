#!/usr/bin/python
import subprocess
import threading
import select
from config_fuzzer import RunParameters

def get_monitor(pkg_name):

    while True:
        package_id = get_package_id(pkg_name)

        print package_id
        cmd = 'adb -s ' + RunParameters.AVD_SERIAL + ' shell logcat | grep controllable_widgets'

        print(cmd)

        p = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE, universal_newlines=True)
        line = p.stdout.readline()
        print "+++++++++++++ " + line

        return p

        # continuously reading from shell processes
        #for line in iter(p.stdout.readline,""):
        #    print "$$$$$$$$$"+line

def get_monitor_proc(pkg_name):
    package_id = get_package_id(pkg_name)
    cmd = 'adb -s ' + RunParameters.AVD_SERIAL + ' shell logcat | grep controllable_widgets'
    print(cmd)
    p = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE, universal_newlines=True)

    print "-- log monitor is on..."
    return p



# returning the hashcode of the app package name
def get_package_id(pkg_name):
    package_id = java_string_hashcode(pkg_name)
    return abs(package_id)


# returning the same hashcode as java's String.hasCode
def java_string_hashcode(string):
    h = 0
    for c in string:
        h = (31 * h + ord(c)) & 0xFFFFFFFF

    return ((h + 0x80000000) & 0xFFFFFFFF) - 0x80000000

# extracting the state id from the output of the shell


class StateMonitor:
    CLOSE = False
    lock = threading.Lock()

    def __init__(self, queue):
        self.adb_logs = queue

    def run_state_monitor(self, pkg_name):
        print(pkg_name)
        logcat = get_monitor(pkg_name)

        print("*************************> running state monitor")

        poll_obj = select.poll()
        poll_obj.register(logcat.stdout, select.POLLIN)

        while True:
            StateMonitor.lock.acquire()
            if StateMonitor.CLOSE:
                StateMonitor.lock.release()
                break
            StateMonitor.lock.release()

            poll_result = poll_obj.poll(0)
            if poll_result:
                line = logcat.stdout.readline()
		if line == '' and logcat.poll() is not None:
                    print('monitor is terminated!')
                    break

                print "In monitor " + line
                self.adb_logs.writeline(line)

        StateMonitor.lock.acquire()
        StateMonitor.CLOSE = False
        StateMonitor.lock.release()
        print("****************************> Closing state monitor. Stopping thread.")

    # called by other threads
    def close(self):
        StateMonitor.lock.acquire()
        StateMonitor.CLOSE = True
        StateMonitor.lock.release()

    def open(self):
        StateMonitor.lock.acquire()
        StateMonitor.CLOSE = False
        StateMonitor.lock.release()

#f __name__ == '__main__':
#    return
