
OUTPUT_DIR=$1
INPUT_DIR=$2

total=0
i=0
for n in $(cat $(find $OUTPUT_DIR -name "cov"))
do
	echo $n
	total=$((total+n))
	i=$((i+1))
done
average=$((total/i))

echo "Covered methods: "$average

Num_methods=$(find $INPUT_DIR -name "covids" | xargs cat | wc -l) 

echo "total methods num: "$Num_methods

echo "method coverage:"$((average*100/Num_methods))"%" 
#echo $Results
	 
