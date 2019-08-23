#!/bin/bash

# download image-building files from google drive 

function gdrive_download () {
	  CONFIRM=$(wget --quiet --save-cookies /tmp/cookies.txt --keep-session-cookies --no-check-certificate "https://docs.google.com/uc?export=download&id=$1" -O- | sed -rn 's/.*confirm=([0-9A-Za-z_]+).*/\1\n/p')
	  wget --load-cookies /tmp/cookies.txt "https://docs.google.com/uc?export=download&confirm=$CONFIRM&id=$1" -O $2

rm -rf /tmp/cookies.txt
}

gdrive_download 1uXGkV-1iqDJ6QDLwlgh63nbZ3LrUvcLc image.zip
unzip image.zip
rm image.zip

#unzip files
unzip image-1.0.zip

#put the project into the image 
cp -r fuzzingandroid image-1.0/hypermonkey/

#building a docker image
cd image-1.0
docker build -t droidtest/hypermonkey:1.0 .


