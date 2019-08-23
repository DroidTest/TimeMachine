import os
import time
from random import randint

class SysEventGenerator:

    def __init__(self, machine_addr, adb_port, run_time):
        self.machine_addr = machine_addr
        self.adb_port = adb_port
        self.time_limit = run_time
        self.start_time = time.time()
    def generate_system_events(self):
        while (time.time() - self.start_time) < self.time_limit:
            #  Generate a system event every 1 minute, keep min 30s between events, max 1 min between events
            time.sleep(randint(60, 90))
            print "!!!!!!!!!!!!!!!!!!!!!!!!!!! Generating system event !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"	
            print "start time:  " + str(self.start_time) + "   budget:  " + str(self.time_limit) + "   now: " + str(time.time())
            cmd = "python ~/fuzzingandroid/sys_event_generator/tester.py -s " + self.machine_addr + ":" + self.adb_port + " -f ~/fuzzingandroid/aut_apk/aut.apk -p random"
            try:
                os.system(cmd)
            except:
                print "Oops, error sending system event!"

        print "System event generator closing"
