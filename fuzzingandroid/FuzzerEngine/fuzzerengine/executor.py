#!/usr/bin/python

import threading
import state_monitor
import fuzzers
import os
import time
import Queue
import sys
import state_graph
import subprocess
import log_watcher
from config_fuzzer import RunParameters
from collections import deque
from adb_messages_queue import AdbMessagesQueue
from sysevent_generator import SysEventGenerator
from circular_restore_strategy import CircularRestoreStrategy
from datetime import datetime
from subprocess import call
import coverage_manager

import csv
import json
from enum import Enum

import select

class LogcatMessageType(Enum):
    STATE_ID = "state_id"
    CONTROLLABLE_WIDGETS = "controllable_widgets"

    def __str__(self):
        return "{0}".format(self.value)


class Executor:

    rootState = None # the root of the state graph
    shortest_eventSq_state = None # a dict storing pairs of the shortest eventSqs and edge_ids

    def __init__(self, monkey, state_graph, strategy, pkg_name, time_limit):

        self.monkey_controller = monkey
        self.state_graph = state_graph
        self.strategy = strategy
        self.adb_messages = AdbMessagesQueue()
        self.logger = log_watcher.LogWatcher(self.adb_messages)
        self.monitor = state_monitor.StateMonitor(self.adb_messages)
        self.sys_event_generator = SysEventGenerator(time_limit)
        self.logger_thread = None
        self.monitor_thread = None
        self.sys_event_thread = None

        self.start_time = time.time()
        self.time_limit = time_limit
        self.pkg_name = pkg_name
        self.state_id_being_fuzzed = "INITIAL"

        self.queue = Queue.Queue()
        self.states = set()
        self.crash_count = 0
        self.num_restore = 0



    def set_app_under_test(self,app_name):
        os.system('adb -s '+ RunParameters.AVD_SERIAL+" shell setprop 'PackageName' '" + app_name + "'")


    def start_monkey(self, app_name):
        print 'starting monkey...'
        mc = self.monkey_controller
        monkey_watcher = threading.Thread(target=mc.run_monkey, args=(app_name, 999999, 200))
        monkey_watcher.start()
        return monkey_watcher

    def stop_monkey(self):
        self.monkey_controller.kill_monkey()

    def start_logger(self):
        # ensure bites is started and a process id exists
        self.logger.open()
        aut_pid = None
        while (aut_pid is None or aut_pid == ''):
            aut_pid = self.logger.get_AUT_PID(self.pkg_name)

        lw = self.logger
        watcher = threading.Thread(target=lw.run_log_watcher, args=(int(aut_pid),))
        self.logger_thread = watcher
        self.logger_thread.start()

    def stop_logger(self):
        self.logger.close()
        self.logger_thread.join()
        self.logger_thread = None


    def start_sys_event_generator(self):
        seg = self.sys_event_generator
        self.sys_event_thread = threading.Thread(target=seg.generate_system_events)
        self.sys_event_thread.setDaemon(True)
        self.sys_event_thread.start()
    def start_crash_watcher(self):
        print "crash watcher starting..."
        crash_t=threading.Thread(target=self.dump_crash_logs)
        crash_t.setDaemon(True)
        crash_t.start()

    def start_output(self, app_package_name, app_class_files_path):

        output_t = threading.Thread(target=self.output, args=(app_package_name, app_class_files_path,))
        output_t.setDaemon(True)
        output_t.start()

    
    
    def run(self, app_name, recent_path_size, timeout, app_class_files_path):
        
        #launching the app
        self.init_app(app_name)
        
        recent_path = deque(maxlen=recent_path_size)
        self.num_restore=0
        start_time=time.time()
        self.start_sys_event_generator()
        self.start_output(app_name, app_class_files_path)

        while (time.time() - start_time) < timeout:

        
            log_proc=state_monitor.get_monitor_proc(app_name)
            monkey_watcher=self.start_monkey(app_name)
            self.start_crash_watcher()

            self.start_exec(log_proc, monkey_watcher, recent_path, recent_path_size, app_name, app_class_files_path)

            coverage_manager.pull_coverage_files(self.num_restore, app_name, app_class_files_path,
                                                 RunParameters.AVD_SERIAL)

            coverage_manager.compute_current_coverage(app_class_files_path)
            current_coverage = coverage_manager.read_current_coverage()
            print "--the current line coverage : " + str(current_coverage)

           # fittest_state = self.strategy.get_fittest_state()
            fittest_state=self.strategy.get_k_neighbours_fittest_state()
            print "--the fittest state is " + str(fittest_state)

            print "--load the fittest state..."
            os.system("../../scripts/load_snapshot.sh " + str(fittest_state) + " " + RunParameters.AVD_PORT + " > /dev/null 2>&1")
            os.system("adb -s " + RunParameters.AVD_SERIAL + " wait-for-device")


            self.monkey_controller.kill_monkey()
            state = self.state_graph.retrieve(fittest_state)
            state.add_restore_count()
            self.state_id_being_fuzzed = fittest_state
            
            self.num_restore=self.num_restore + 1
            print "Num of restores: " + str(self.num_restore)

        ## terminate
        print 'Timeout reached. Terminating...'
        self.monkey_controller.kill_monkey()


    def start_exec(self, log_proc, monkey_watcher, recent_path, recent_path_size, app_package_name, app_class_files_path):
        
        current_coverage = coverage_manager.read_current_coverage()
        event_num=0    
        
        log_watcher=select.poll()
        log_watcher.register(log_proc.stdout, select.POLLIN)

        while True:
            line=""
            if log_proc.poll() != None or not monkey_watcher.isAlive():
                print "no app info in logs ---"
                break
            
            try:
                if log_watcher.poll(1):
                   line=log_proc.stdout.readline()
                else:
                   continue
            except select.error:
                print "select.error catched!"
                pass
        
            #parsing the line, skip if the line is empty or there is no state_id
            state_info = self.parse_line(line)
            if state_info is None:
                continue

            id = str(state_info[str(LogcatMessageType.STATE_ID)])
            num_widgets = state_info[str(LogcatMessageType.CONTROLLABLE_WIDGETS)]
            print "--extracted id: "+str(id) +"  num_controllable_widgets: " + str(num_widgets) 

            if id is None:
                continue

            #case where events do not trigger state transition
            if id == self.state_id_being_fuzzed:
                event_num=event_num+1
                print "--event num: " + str(event_num)
                if event_num > 200:
                    #penalize the state
                    state_being_fuzzed = self.state_graph.retrieve(self.state_id_being_fuzzed)
                    if state_being_fuzzed is not None:
                        state_being_fuzzed.add_transition_to_existing_state()
                    #clear observed states
                    recent_path.clear()
                    break

                #bring app under test to front
                if  self.state_id_being_fuzzed == "OOAUT":
                    self.bring_app_to_front(self.pkg_name)
                    
            
            #case where events trigger state transition
            else:
                print "--transiton occurs"
                

                if self.state_graph.is_exist(id):
                    print "--the state " + str(id) +"  exists"
                    self.state_graph.add_edge(self.state_id_being_fuzzed, id)
                    
                    parent = self.state_graph.retrieve(self.state_id_being_fuzzed)
                    parent.add_transition_to_existing_state()
                else:
                    print "--a new state is triggered and add " + str(id) + " into the state graph."

                    self.state_graph.add_node(id)
                    self.state_graph.add_edge(self.state_id_being_fuzzed, id)

                        #rewarding
                    parent = self.state_graph.retrieve(self.state_id_being_fuzzed)
                    child = self.state_graph.retrieve(id)

                    coverage_manager.pull_coverage_files("temp", app_package_name, app_class_files_path,
                                                         RunParameters.AVD_SERIAL)

                    coverage_manager.compute_current_coverage(app_class_files_path)  # output in coverage.txt
                    new_coverage = coverage_manager.read_current_coverage()
                    print "--coverage when the new state is triggered: " + str(new_coverage) + " current jacoco line coverage rate : " + str(current_coverage)
                    
                    
                    if new_coverage > current_coverage:
                        parent.add_transition_to_high_coverage(child)
                            
                        if id != "OOAUT": 
                            print "--taking snapshot..."
                            os.system("../../scripts/take_snapshot.sh " + id + " " + RunParameters.AVD_PORT + " > /dev/null 2>&1")

                            self.state_graph.retrieve(id).solid = True

                            current_coverage = new_coverage


                    else:
                        parent.add_transition_to_low_coverage(child)

                self.state_graph.retrieve(id).set_controllable_widgets(int(num_widgets))
                   
 
                self.state_id_being_fuzzed = id 
                event_num=0
                recent_path.append(id)
                
                #logging
                print(recent_path)
                print('the portion to the top 20% most frequent states: ' + str(self.state_graph.compute_frequent_node_portion(list(recent_path), 0.2)))
                #print "--length: "+str(len(recent_path))
                #print(self.state_graph.dump())

                
                if len(recent_path) == recent_path_size and self.state_graph.compute_frequent_node_portion(list(recent_path), 0.2) >= 0.8:
                    recent_path.clear()
                    self.num_restore = self.num_restore +1
                    break

    def bring_app_to_front(self,pkg_name):

        try:
            cmd="adb -s " + RunParameters.AVD_SERIAL +" shell dumpsys activity recents | grep realActivity="+ pkg_name + "  | cut -d'=' -f2 | xargs adb -s " + RunParameters.AVD_SERIAL +" shell am start "
            os.system(cmd + " > /dev/null 2>&1")
        except Exception, e:
            print "resumed app failed."


    def init_app(self, app_name):

        print "launching app under test..."
        cmd="adb -s " + RunParameters.AVD_SERIAL + " shell monkey -p " + app_name + "  1"
        print cmd
        while True:
            p = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            output = str(p.stdout.read()).strip()
            if "No activities found to run" in output:
                print "ERROR: launch failed! Try Again!"
                time.sleep(5)
            elif "Events injected" in output:
                print "SUCCESS: app is launched!"
                break
            else:
                print "New Message"
                break
        print "takes a while to complete starting animation ..."
        time.sleep(5)

        self.bring_app_to_front(self.pkg_name)
        time.sleep(5)

        print "--taking snapshot for the initial state..."
        self.state_graph.add_node("INITIAL")
        os.system("../../scripts/take_snapshot.sh INITIAL "+RunParameters.AVD_PORT +" > /dev/null 2>&1")
        self.state_graph.retrieve("INITIAL").solid = True

    def get_state_id(self, line):
        # Android4.4 log format: I/1317239282( 2494): 1017167544

        # the hash code of the app package name
        package_id = state_monitor.get_state_id(self.pkg_name)
        #print "pkg id " + str(package_id)

        if str(package_id) not in line:
            return None

        # delimiter
        delimiter = ":"
        s = line.split(delimiter)

        # there are no state id for some lines
        if len(s) > 1:
            return s[1].strip()
        else:
            return None

    def parse_line(self, line):
        #print "Parsing line: " + line
        # Ensure the package id is that of the target app
        package_id = state_monitor.get_package_id(self.pkg_name)
        if str(package_id) not in line:
            return None

        delimiter = str(package_id)+":"
        s = line.split(delimiter, 1)
        if len(s) <= 1:
            return None

        try:
            info = json.loads(s[1])
        except ValueError, e:
            return None

        return info
    
    def output(self, app_package_name, app_class_files_path):
        
        with open(RunParameters.OUTPUT_FILE, "a") as csv_file:
            writer = csv.writer(csv_file, delimiter=',', quotechar='|', quoting=csv.QUOTE_MINIMAL)
            while (time.time() - self.start_time) < self.time_limit:
                
                #computing snapshots
                num_snapshots = 0
                for key in self.state_graph.states:
                    if self.state_graph.states[key].solid:
                        num_snapshots = num_snapshots+1
                
                #read coverage
                coverage_manager.pull_coverage_files("temp", app_package_name, app_class_files_path,
                                                     RunParameters.AVD_SERIAL)

                coverage_manager.compute_current_coverage(app_class_files_path)             # output in coverage.txt
                current_coverage = coverage_manager.read_current_coverage()

                # write files
                writer.writerow([str(int(time.time()-self.start_time)), str(len(self.state_graph.states)),str(num_snapshots), str(self.num_restore), str(current_coverage)])
                time.sleep(120)
                
                print "current threads:  " + str(threading.active_count())


        csv_file.close()         

    def dump_crash_logs(self):
        
        cmd = "adb -s " + RunParameters.AVD_SERIAL +" logcat AndroidRuntime:E *:S"
        p = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE, universal_newlines=True, close_fds=True)
        fw = open(RunParameters.CRASH_FILE, "a")
        while True:
            line = p.stdout.readline()
            fw.write(line)

            if p.poll() != None:
                print "crash watcher is termined..."
                break

        now = datetime.now()
        current_time = now.strftime("%Y-%m-%d-%H:%M:%S")
        fw.write("[" + current_time + "]" + "\n")
        fw.close()


if __name__ == '__main__':
    RunParameters.APP_DIR = sys.argv[1]
    RunParameters.RUN_PKG = sys.argv[2]
    RunParameters.APK_FILE_NAME = sys.argv[3]
    RunParameters.RUN_TIME = float(sys.argv[4])
    RunParameters.OUTPUT_DIR = sys.argv[5]
    RunParameters.AVD_SERIAL = sys.argv[6]
    RunParameters.AVD_PORT = sys.argv[7]
    RunParameters.AVD_NAME = sys.argv[8]

    RunParameters.OUTPUT_FILE= RunParameters.OUTPUT_DIR  +  "/data.csv"
    RunParameters.CRASH_FILE= RunParameters.OUTPUT_DIR  +  "/crashes.log"
    RunParameters.RUN_TIME_FILE = RunParameters.OUTPUT_DIR  +  "/run_time.log"

    graph = state_graph.StateGraph()
    monkey_controller = fuzzers.MonkeyController()

    strategy = CircularRestoreStrategy(graph, 3)
    executor = Executor( monkey_controller, graph, strategy, RunParameters.RUN_PKG, RunParameters.RUN_TIME)
    executor.set_app_under_test(RunParameters.RUN_PKG)
    time.sleep(3)

    APP_CLASS_FILES = RunParameters.APP_DIR+"/class_files.json"
    import json
    tmp_file = open(APP_CLASS_FILES, "r")
    tmp_file_dict = json.load(tmp_file)
    tmp_file.close()
    app_path_info_dict = tmp_file_dict[RunParameters.APK_FILE_NAME]
    class_files_path_list = app_path_info_dict['classfiles']
    CLASS_FILES_PATH = ""
    for class_files_path in class_files_path_list:
        CLASS_FILES_PATH += ' --classfiles ' + os.path.join(RunParameters.APP_DIR , class_files_path)
    print "CLASS_FILE_PATH: " + CLASS_FILES_PATH

    # record testing starting time
    fw = open(RunParameters.RUN_TIME_FILE, "a")
    now = datetime.now()
    current_time = now.strftime("%Y-%m-%d-%H:%M:%S")
    fw.write(current_time)
    fw.close()
   
    executor.run(RunParameters.RUN_PKG, 10, RunParameters.RUN_TIME, CLASS_FILES_PATH)

    # record testing ending time
    fw = open(RunParameters.RUN_TIME_FILE, "a")
    now = datetime.now()
    current_time = now.strftime("%Y-%m-%d-%H:%M:%S")
    fw.write(current_time + "\n")
    fw.close()

    graph.dump()

