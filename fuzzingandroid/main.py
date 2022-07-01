import os
import subprocess
import time
from argparse import ArgumentParser
from multiprocessing.pool import ThreadPool

def get_time_in_seconds(testing_time):
    if 'h' in testing_time:
        testing_time_in_secs = int(testing_time[:-1]) * 60 * 60
    elif 'm' in testing_time:
        testing_time_in_secs = int(testing_time[:-1]) * 60
    elif 's' in testing_time:
        testing_time_in_secs = int(testing_time[:-1])
    else:
        print("Warning: the given time is ZERO seconds!!")
        testing_time_in_secs = 0  # error!

    return testing_time_in_secs

def run_timemachine(apk, avd_serial, avd_name, output_dir, testing_time, screen_option,  adb_port):
    testing_time_in_secs = get_time_in_seconds(testing_time)

    command = 'bash run_timemachine.sh %s %s %s %s %s %s %s' % (apk, avd_serial, avd_name,
                                                                   output_dir,
                                                                   testing_time_in_secs,
                                                                   screen_option,
                                                                   adb_port)
    print('execute timemachine: %s' % command)

    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    while True:
        output=p.stdout.readline().strip()
        print output
        if "device offline" in output:
            print "==============================>restart adb server!<======================================="
            os.system("adb kill-server > /dev/null")
            os.system("adb devices > /dev/null")
            os.system("adb -s "+avd_serial+"wait-for-device > /dev/null")

        if p.poll() != None:
            print "output watcher is termined..."
            break

    # os.system(command)


def get_all_apks(apk_list_file):
    file = open(apk_list_file, 'r')
    apk_paths = []
    for line in file.readlines():
        if line.strip().startswith('#'):
            # skip commented apk files
            continue
        if "," in line:
            content = line.split(",")
            apk_paths.append(content[0].strip())
        else:
            apk_path = line.strip()
            apk_paths.append(apk_path)
    print("Total %s apks under test" % len(apk_paths))
    return apk_paths

def main(args):
    if not os.path.exists(args.o):
        os.mkdir(args.o)

    # allocate emulators for an apk
    start_avd_serial = 5554 + args.offset * 2
    avd_serial_list = []
    for apk_index in range(args.number_of_devices):
        avd_serial = 'emulator-' + str(start_avd_serial + apk_index * 2)
        avd_serial_list.append(avd_serial)
        print('allocate emulators: %s' % avd_serial)

    if args.no_headless:
        screen_option = "\"\""
    else:
        screen_option = "-no-window"

    if args.apk is not None:
        # single apk mode
        all_apks = [args.apk]
    else:
        # multiple apks mode
        all_apks = get_all_apks(args.apk_list)

    if args.repeat > 1:
        copy_all_apks = all_apks.copy()
        for i in range(1, args.repeat):
            all_apks = all_apks + copy_all_apks

    print("the apk list to fuzz: %s" % str(all_apks))

    number_of_apks = len(all_apks)
    apk_index = 0

    while 0 <= apk_index < number_of_apks:

        p = ThreadPool(args.number_of_devices)
        for avd_serial in avd_serial_list:
            time.sleep(10)
            if apk_index >= number_of_apks:
                break
            current_apk = all_apks[apk_index]

            print(os.path.exists(current_apk))

            print("Now allocate the apk: %s on %s" % (current_apk, avd_serial))


            avd_port = avd_serial.split('-')[1]
            p.apply_async(run_timemachine, args=(current_apk, avd_serial, args.avd_name,
                                                    args.o, args.time, screen_option,
                                                    avd_port,))

            apk_index += 1

        print("wait the allocated devices to finish...")
        p.close()
        p.join()

if __name__ == '__main__':
    ap = ArgumentParser()

    # by default, we run each bug/tool for 6h & 5r.
    # Each emulator is configured as 2GB RAM, 1GB internal storage and 1GB SDCard

    ap.add_argument('--avd', type=str, dest='avd_name', help="the device name")
    ap.add_argument('--apk', type=str, dest='apk')
    ap.add_argument('-n', type=int, dest='number_of_devices', default=1,
            help="number of emulators created for testing, default: 1")
    ap.add_argument('--apk-list', type=str, dest='apk_list', help="list of apks under test")
    ap.add_argument('-o', required=True, help="output dir")
    ap.add_argument('--time', type=str, default='6h', help="the fuzzing time in hours (e.g., 6h), minutes (e.g., 6m), or seconds (e.g., 6s), default: 6h")
    ap.add_argument('--repeat', type=int, default=1, help="the repeated number of runs, default: 1")
    ap.add_argument('--no-headless', dest='no_headless', default=False, action='store_true', help="show gui")
    ap.add_argument('--offset', type=int, default=0, help="device offset number w.r.t emulator-5554")

    args = ap.parse_args()

    if args.number_of_devices + args.offset > 16:
        ap.error('n + offset should not be ge 16')

    if args.apk is None and args.apk_list is None:
        ap.error('please specify an apk or an apk list')

    if args.apk_list is not None and not os.path.exists(args.apk_list):
        ap.error('No such file: %s' % args.apk_list)

    if 'h' not in args.time and 'm' not in args.time and 's' not in args.time:
        ap.error('incorrect time format, should be appended with h, m, or s')

    main(args)
