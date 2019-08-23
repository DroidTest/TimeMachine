#!/usr/bin/python

import tempfile
from subprocess import check_output, CalledProcessError, call

import adb_var as v


def runMonkey(opts=[]):
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_SHELL, v.ADB_COMMAND_MONKEY, _convert_opt(opts)]
    return _exec_command(adb_full_cmd)


def _isDeviceAvailable():
    result = getserialno()
    if result[1].strip() == "unknown":
        return False
    else:
        return True


def devices(opts=[]):
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_DEVICES, _convert_opt(opts)]
    _exec_command(adb_full_cmd)


def wait_for_device():
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_WAITFORDEVICE]
    return _exec_command(adb_full_cmd)


def getserialno():
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_GETSERIALNO]
    return _exec_command(adb_full_cmd)


def sync():
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_SHELL, v.ADB_COMMAND_SYNC]
    return _exec_command(adb_full_cmd)


def start_server():
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_START_SERVER]
    return _exec_command(adb_full_cmd)


def kill_server():
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_KILL_SERVER]
    return _exec_command(adb_full_cmd)


def get_state():
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_GET_STATE]
    return _exec_command(adb_full_cmd)


def install(apk, opts=[]):
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_INSTALL, _convert_opt(opts), apk]
    return _exec_command(adb_full_cmd)


def uninstall(app, opts=[]):
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_UNINSTALL, _convert_opts(opts), app]
    return _exec_command(adb_full_cmd)


def shell(cmd):
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_SHELL, cmd]
    return _exec_command(adb_full_cmd)


def pull(src, dest):
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_PULL]
    return _exec_command(adb_full_cmd)


def push(src, dest):
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_PUSH]
    return _exec_command(adb_full_cmd)


def connect(ip):
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_CONNECT, ip]
    return _exec_command(adb_full_cmd)


def version():
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_VERSION]
    return _exec_command(adb_full_cmd)


def _convert_opt(opts):
    return ' '.join(opts)


#adb shell am broadcast -a edu.gatech.m3.emma.COLLECT_COVERAGE
def collect_coverage():
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_SHELL, 'am broadcast -a edu.gatech.m3.emma.COLLECT_COVERAGE' ]
    return _exec_command(adb_full_cmd)

def _exec_command(adb_cmd):
    """
    Format adb command and execute it in shell
    :param adb_cmd: list adb command to execute
    :return: string '0' and shell output if successful, otherwise raise CalledProcessError exception and return error code
    """
    t = tempfile.TemporaryFile()
    final_adb_cmd = []
    for e in adb_cmd:
        if e != '':  # avoid items with empty string
            final_adb_cmd.append(e)
    print('\n*** Executing' + ' '.join(adb_cmd) + ' ' + 'command')

    try:
        output = check_output(final_adb_cmd, stderr=t)
    except CalledProcessError as e:
        t.seek(0)
        result = e.returncode, t.read()
    else:
        result = 0, output
        print('\n' + result[1])

    return result


def bugreport(dest_file="default.log"):
    """
    Prints dumpsys, dumpstate, and logcat data to the screen, for the purposes of bug reporting
    :return: result of _exec_command() execution
    """
    adb_full_cmd = [v.ADB_COMMAND_PREFIX, v.ADB_COMMAND_BUGREPORT]
    try:
        dest_file_handler = open(dest_file, "w")
    except IOError:
        print "IOError: Failed to create a log file"

    # We have to check if device is available or not before executing this command
    # as adb bugreport will wait-for-device infinitely and does not come out of
    # loop
    # Execute only if device is available only
    if _isDeviceAvailable():
        result = _exec_command_to_file(adb_full_cmd, dest_file_handler)
        return (result, "Success: Bug report saved to: " + dest_file)
    else:

        return (0, "Device Not Found")


def _exec_command_to_file(adb_cmd, dest_file_handler):
    """
    Format adb command and execute it in shell and redirects to a file
    :param adb_cmd: list adb command to execute
    :param dest_file_handler: file handler to which output will be redirected
    :return: string '0' and writes shell command output to file if successful, otherwise
    raise CalledProcessError exception and return error code
    """
    t = tempfile.TemporaryFile()
    final_adb_cmd = []
    for e in adb_cmd:
        if e != '':  # avoid items with empty string...
            final_adb_cmd.append(e)  # ... so that final command doesn't
            # contain extra spaces
    print('\n*** Executing ' + ' '.join(adb_cmd) + ' ' + 'command')

    try:
        output = call(final_adb_cmd, stdout=dest_file_handler, stderr=t)
    except CalledProcessError as e:
        t.seek(0)
        result = e.returncode, t.read()
    else:
        result = output
        dest_file_handler.close()

        return result

if __name__ == '__main__':
    version()
    connect('192.168.56.102:5555')
    runMonkey(['-p com.android.contacts', '-v 1000'])