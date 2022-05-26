import sys
import os
from pyexpat import ExpatError
from xml.dom import minidom


def add_jacoco_class(source_code_package_name,source_code_path):
    current_dir_path = os.path.split(os.path.realpath(__file__))[0]
    os.system("cp -r " + current_dir_path + "/JacocoInstrument "+source_code_path)
    if os.path.isdir(source_code_path+"/JacocoInstrument"):
        add_package_name_to_java_class(source_code_package_name,source_code_path)
        print "JacocoInstrument dir added successfully."

    else:
        print "JacocoInstrument dir added failed."
    return


def add_package_name_to_java_class(source_code_package_name,source_code_path):
    try:
        with open(source_code_path+"/JacocoInstrument/FinishListener.java", "r+") as f1:
            old = f1.read()
            f1.seek(0)
            f1.write("package "+source_code_package_name+".JacocoInstrument;\n")
            f1.write(old)
            f1.close()
        with open(source_code_path+"/JacocoInstrument/JacocoInstrumentation.java", "r+") as f2:
            old = f2.read()
            f2.seek(0)
            f2.write("package "+source_code_package_name+".JacocoInstrument;\n")
            f2.write(old)
            f2.close()
        with open(source_code_path+"/JacocoInstrument/SMSInstrumentedReceiver.java", "r+") as f3:
            old = f3.read()
            f3.seek(0)
            f3.write("package "+source_code_package_name+".JacocoInstrument;\n")
            f3.write(old)
            f3.close()
    except IOError:
        print "read java files error."

    return





def regist_in_xml(xml_path):
    try:
        xml_tree = minidom.parse(xml_path)
        root_node = xml_tree.documentElement
        application_node = root_node.getElementsByTagName('application')[0]

        receiver_node_list = application_node.getElementsByTagName('receiver')
        for node in receiver_node_list:
            if node.getAttribute("android:name") == ".JacocoInstrument.SMSInstrumentedReceiver":
                return

        receiver_node = xml_tree.createElement("receiver")
        receiver_node.setAttribute("android:name",".JacocoInstrument.SMSInstrumentedReceiver")

        intent_filter_node = xml_tree.createElement("intent-filter")

        action_node = xml_tree.createElement("action")
        action_node.setAttribute("android:name","edu.gatech.m3.emma.COLLECT_COVERAGE")

        intent_filter_node.appendChild(action_node)
        receiver_node.appendChild(intent_filter_node)
        application_node.appendChild(receiver_node)

        with open(xml_path,"w") as xml:
            xml_tree.writexml(xml,addindent="",encoding="utf-8")
        print "Receiver regist in AndroidManifest.xml successfully."
    except ExpatError:
        print "parse xml error!"

    return



def add_plugin_in_build_gradle(module_path):
    try:
        with open(module_path+"/build.gradle", "r+") as gradle_file:
            old = gradle_file.read()
            gradle_file.seek(0)
            gradle_file.write("apply plugin: 'jacoco' \n")
            gradle_file.write(old)
            gradle_file.close()
            print "build.gradle modified successfully."
    except IOError:
        print "read build.gradle error."

    return

def auto_jacoco_integrate(module_path, source_code_package_name):

    source_code_path = module_path+"/src/main/java/"+source_code_package_name.replace(".","/")
    xml_path = module_path + "/src/main/AndroidManifest.xml"

    if not os.path.exists(module_path):
        print "no such module dir, check your module path. "
        return

    if not os.path.exists(source_code_path):
        print "no such source code dir, check your source code path. "
        return

    if not os.path.isfile(xml_path):
        print "Androidmanifest.xml not found, check your code structure. "
        return

    if not os.path.isfile(module_path+'/build.gradle'):
        print "build.gradle not found, check your module structure. "
        return

    add_jacoco_class(source_code_package_name,source_code_path)
    regist_in_xml(xml_path)
    add_plugin_in_build_gradle(module_path)

    current_dir_path = os.path.split(os.path.realpath(__file__))[0]
    os.system("./build_gradle.sh "+module_path)

    return

def main():
    module_path = sys.argv[1]
    source_code_package_name = sys.argv[2]

    auto_jacoco_integrate(module_path, source_code_package_name)


if __name__ == '__main__':
     main()