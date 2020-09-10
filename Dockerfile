#image for Unbuntu-16.04 
#FROM zhendong2050/android-x86-7.1  
FROM zhendong2050/android-x86-7.1-ubuntu-18.04

MAINTAINER zhendng@gmail.com

WORKDIR /root
ENV DEBIAN_FRONTEND noninteractive

RUN  apt-get update && apt-get install -y linux-headers-$(uname -r) virtualbox-dkms virtualbox virtualbox-qt  python-pip openjdk-8-jdk git-core \
     && pip install pyvbox enum vbox-sdk uiautomator  


#super important: setting enviroment variables
ENV VBOX_INSTALL_PATH=/usr/lib/virtualbox
ENV VBOX_SDK_PATH=/usr/lib/virtualbox/sdk
ENV PYTHONPATH=/usr/lib/virtualbox/sdk/bindings/xpcom/python

ENV PATH="/root/Android/Sdk/build-tools/26.0.2:/root/Android/Sdk/platform-tools:/root/Android/Sdk/tools:/root/Android/Sdk/build-tools/:${PATH}"

COPY fuzzingandroid /root/fuzzingandroid/
# install virtualbox sdk
RUN cd vbox_sdk/installer && python vboxapisetup.py install

#add VBoxpython2.7 
RUN cp fuzzingandroid/libs/VBoxPython2_7.so /usr/lib/virtualbox/
# clone the project
#RUN git clone https://github.com/zhendong2050/3TDroid.git


