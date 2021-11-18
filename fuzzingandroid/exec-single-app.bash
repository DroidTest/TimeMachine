#!/bin/bash

set -e

readonly ELLA_OUTPUT_DIR=$1
readonly OPEN_SOURCE=$2
readonly DOCKERIMAGE=$3 
readonly TIMEOUT=$4
readonly OUTPUT_PATH=${5:-`mktemp -d`}

#readonly DOCKERIMAGE=${DOCKERIMAGE:-'droidtest/hypermonkey:1.0'}

if (( $# < 5 )); then
    echo 'USAGE: exec-single.bash APP_DIR OPEN_SOURCE DOCKER_IMAGE TIMEOUT [OUTPUT_PATH]'
    exit 1
fi


load_dkms()
{

        MODULE="vboxdrv"
        if lsmod | grep "$MODULE" &> /dev/null ; then
                 echo "$MODULE is loaded!"
        else
                echo "Loading $MODULE from a container"
                #insmod $(find /lib/modules/ -name $MODULE".ko")
                docker run -d --rm --privileged=true -u root --workdir /root/fuzzingandroid "${DOCKERIMAGE}" \
                bash -l -x -c "bash ./load_dkms.sh" 
                sleep 5
        fi
}
# load dkms in a walkaround solution
load_dkms

mkdir -p "${OUTPUT_PATH}"

readonly OUTPUT_HYPERMONKEY_PATH=`realpath "${OUTPUT_PATH}/timemachine-output"`
mkdir -p "${OUTPUT_HYPERMONKEY_PATH}"
chmod 0777 "${OUTPUT_HYPERMONKEY_PATH}"

readonly appDir=`realpath "${ELLA_OUTPUT_DIR}"`
readonly apkPath=${appDir}/instrumented.apk
#readonly apkName=$(aapt dump badging "$apkPath" | grep package | awk '{print $2}' | sed s/name=//g | sed s/\'//g)

readonly EXEC_ID=${EXEC_ID:-"timemachine-run"}

echo "Start processing $appDir"
 
readonly OUTPUT_LOG_PATH="$OUTPUT_PATH/timemachine-run.log"

time stdbuf -o0 -e0 docker run -t -a stdout -a stderr --name "${EXEC_ID}-`date +'%F-%H-%M-%S'`" \
    --device /dev/vboxdrv:/dev/vboxdrv --privileged=true -u root \
    --workdir /root/fuzzingandroid -v "$appDir":/root/app:ro \
    -v "${OUTPUT_HYPERMONKEY_PATH}":/root/fuzzingandroid/output:rw "${DOCKERIMAGE}" \
    bash -l -x -c "bash -x ./start.bash ~/app 1 $OPEN_SOURCE $TIMEOUT" 2>&1 \
        | tee -a  "$OUTPUT_LOG_PATH"

(( ${PIPESTATUS[0]} != 0 )) && (echo "Error processing $appDir !" | tee -a "$OUTPUT_LOG_PATH") && exit 1
echo "Done processing $appDir" | tee -a "$OUTPUT_LOG_PATH"
