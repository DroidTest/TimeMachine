import subprocess
from config_fuzzer import RunParameters
import select
import threading


class LogWatcher:
    CLOSE = False
    lock = threading.Lock()

    def __init__(self, queue):
        self.adb_logs = queue

    def get_AUT_PID(self, package_name):
        try:
            cmd = 'adb -s ' + RunParameters.AVD_SERIAL + " shell pidof -s " + package_name
            pid = subprocess.check_output(cmd.split()).strip()
        except:
            return None

        return pid

    # crash logs can be found here
    def get_process_logs(self, aut_pid):
        cmd = "adb -s " + RunParameters.AVD_SERIAL + " logcat | grep -e " + str(aut_pid) + " -e controllable_widgets"
        p = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE, universal_newlines=True)
        return p

    def run_log_watcher(self, aut_pid):
        logcat = self.get_process_logs(aut_pid)

        print("=========================> running log watcher")

        poll_obj = select.poll()
        poll_obj.register(logcat.stdout, select.POLLIN)

        while True:
            LogWatcher.lock.acquire()
            if LogWatcher.CLOSE:
                LogWatcher.lock.release()
                break
            LogWatcher.lock.release()

            poll_result = poll_obj.poll(0)
            if poll_result:
                line = logcat.stdout.readline()
                lc_line = line.lower()

                spamwriter = open(RunParameters.CRASH_FILE, 'a')
                spamwriter.write(line)

                if lc_line.startswith("e/") and "exception" in lc_line and str(aut_pid) in lc_line:
                    print("Setting error")
                    print lc_line
                    self.adb_logs.writeline(lc_line)

                if "controllable_widgets" in lc_line:
                    print "In monitor " + lc_line
                    self.adb_logs.writeline(lc_line)

                if line == '' and logcat.poll() is not None:
                    print('logcat watcher is terminated!')
                    break


        LogWatcher.lock.acquire()
        LogWatcher.CLOSE = False
        LogWatcher.lock.release()
        print("==========================> Closing log watcher. Stopping thread.")

    def close(self):
        LogWatcher.lock.acquire()
        LogWatcher.CLOSE = True
        LogWatcher.lock.release()

    def open(self):
        LogWatcher.lock.acquire()
        LogWatcher.CLOSE = False
        LogWatcher.lock.release()
