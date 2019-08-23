#!/bin/bash
COUNTER=0
while [  $COUNTER -lt $(($1)) ]; do	
	python tester.py -s 127.0.0.1:6000 -f ~/benchmarks/anymemo/MainTabs-debug.apk -p random
	COUNTER=$(($COUNTER + 1))
done
