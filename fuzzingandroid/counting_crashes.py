#! /usr/bin/env python
import sys
import re
import os

class Crashes_counter:

    crashes=set()
    header_pattern="FATAL EXCEPTION:"
    
    def read_file(self,file_path):

        content=''
        with open(file_path, 'r') as file_reader:
            content=file_reader.read()
        
        return content

    def cut_logs(self, text):
        

        find_pattern = r'%s.*?(?=%s|$)' % (self.header_pattern,self.header_pattern)
        parts = re.findall(find_pattern, text, re.DOTALL)
        return parts

        
    def is_no_targetedpkg(self, pkg, crash):

        if pkg not in crash:
            return True
        else:
            return False

    def extract_PID(self, crash):

        #the seond line
        crash=crash.split('\n')
        process_info=crash[1]
        pid=process_info.split("PID:")[1].strip()
        return crash, pid
            
    def extract_app_stacks(self,crash,PID):
        stack=[]
        #remove first line and add header_pattern 
        iterlines=iter(crash)
        first_line=next(iterlines)

        stack.append(self.header_pattern)

        for line in iterlines:
            if PID not in line:
                break
            #remove time and error type 
	    if len(line.split("AndroidRuntime")) < 2:
		continue
            tmp=line.split("AndroidRuntime:")[1].strip()
            if crash.index(line) == 1:
                tmp = tmp.split("PID:")[0]
            if crash.index(line) ==2:
                tmp = tmp.split(":")[0]
            if tmp.startswith("Caused by:"):
                l=tmp.split(":",2)[:2]
                tmp=str(l[0]+":"+l[1])
            stack.append(tmp)
        
        return stack

    def counting(self, file_path, pkg_name):
        content = self.read_file(file_path)
        chunks = self.cut_logs(content)
        for chunk in chunks:
            if self.is_no_targetedpkg(pkg_name,chunk):
                continue
            crash, pid = self.extract_PID(chunk)
            stacks = self.extract_app_stacks(crash,pid)
            traces=""
            for stack in stacks:
                traces=traces + stack +"\n"

            app_pkg_stack = "at "+str(pkg_name)
            print "pattern: " + app_pkg_stack
            print traces
            if app_pkg_stack in traces:
               print "add one"
               self.crashes.add(traces)
    
    def dump(self,store_path):
        i=0
        os.system("rm " + store_path+"/*.uuuu")
        for element in self.crashes:
            with open(store_path+"/" +str(i) +".uuuu", "w") as f:
                f.write(element)
            i=i+1
        print "total unique crashes : " + str(i)
    
    def count_multi_files(self,pkg_name, files):
        
        for f in files:
            self.counting(f,pkg_name)
def main():
    pkg_name=sys.argv[1]
    output_file = sys.argv[2]
    files=sys.argv[3:]
    counter=Crashes_counter()
    counter.count_multi_files(pkg_name,files)
    counter.dump(output_file)

if __name__ == '__main__':
        main()



