from Queue import Queue


class AdbMessagesQueue:
    def __init__(self):
        self.adb_messages = Queue(maxsize=0)

    def writeline(self, line):
        self.adb_messages.put(line)

    def get(self):
        return self.adb_messages