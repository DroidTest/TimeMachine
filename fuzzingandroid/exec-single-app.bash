#!/bin/bash

set -e

readonly ELLA_OUTPUT_DIR=$1
readonly TIMEOUT=$2
readonly OUTPUT_PATH=${3:-`mktemp -d`}

readonly DOCKERIMAGE=${DOCKERIMAGE:-'droidtest/hypermonkey:1.0'}

if (( $# < 2 )); then
    echo 'USAGE: exec-single.bash ELLA_OUTPUT_DIR TIMEOUT [OUTPUT_PATH]'
    exit 1
fi

mkdir -p "${OUTPUT_PATH}"

readonly OUTPUT_HYPERMONKEY_PATH=`realpath "${OUTPUT_PATH}/hypermonkey-output"`
mkdir -p "${OUTPUT_HYPERMONKEY_PATH}"
chmod 0777 "${OUTPUT_HYPERMONKEY_PATH}"

readonly appDir=`realpath "${ELLA_OUTPUT_DIR}"`
readonly apkPath=${appDir}/instrumented.apk
readonly apkName=$(aapt dump badging "$apkPath" | grep package | awk '{print $2}' | sed s/name=//g | sed s/\'//g)

readonly EXEC_ID=${EXEC_ID:-"hypermonkey-run-$apkName"}

echo "Start processing $apkName"
 
readonly OUTPUT_LOG_PATH="$OUTPUT_PATH/hypermonkey-run.log"

time stdbuf -o0 -e0 docker run -t -a stdout -a stderr --name "${EXEC_ID}-`date +'%F-%H-%M-%S'`" \
    --device /dev/vboxdrv:/dev/vboxdrv --privileged=true -u hypermonkey \
    --workdir /home/hypermonkey/fuzzingandroid -v "$appDir":/home/hypermonkey/app:ro \
    -v "${OUTPUT_HYPERMONKEY_PATH}":/home/hypermonkey/fuzzingandroid/output:rw "${DOCKERIMAGE}" \
    bash -l -x -c "bash -x ./start.bash ~/app 1 "$TIMEOUT 2>&1 \
        | tee -a  "$OUTPUT_LOG_PATH"

(( ${PIPESTATUS[0]} != 0 )) && (echo "Error processing $apkName !" | tee -a "$OUTPUT_LOG_PATH") && exit 1
echo "Done processing $apkName" | tee -a "$OUTPUT_LOG_PATH"
