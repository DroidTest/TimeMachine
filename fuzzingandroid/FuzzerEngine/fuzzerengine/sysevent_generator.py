import os
import time
from random import randint

from config_fuzzer import RunParameters

class SysEventGenerator:

    def __init__(self, run_time):
        self.time_limit = run_time
        self.start_time = time.time()
    def generate_system_events(self):
        while (time.time() - self.start_time) < self.time_limit:
            #  Generate a system event every 1 minute, keep min 30s between events, max 1 min between events
            time.sleep(randint(60, 90))
            print "!!!!!!!!!!!!!!!!!!!!!!!!!!! Generating system event !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
            print "start time:  " + str(self.start_time) + "   budget:  " + str(self.time_limit) + "   now: " + str(time.time())
            cmd = "python2.7 ../../sys_event_generator/tester.py -s " + RunParameters.AVD_SERIAL + " -f "+ RunParameters.APP_DIR + "/" + RunParameters.APK_FILE_NAME + " -p random"
            try:
                os.system(cmd)
            except:
                print "Oops, error sending system event!"

        print "System event generator closing"
