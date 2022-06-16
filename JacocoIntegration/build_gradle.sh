#!/bin/sh

MODULE_PATH=$1

PRO_PATH=`dirname $MODULE_PATH`

cd $PRO_PATH

echo "rebuild gradle project..."

./gradlew clean
./gradlew assembleDebug