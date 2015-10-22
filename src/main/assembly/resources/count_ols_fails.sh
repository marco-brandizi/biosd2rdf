#!/bin/sh

# Estimates the no of failed ZOOMA/Bioportal calls, using log messages
# These are generated by the code, as long as it's used and enabled with a reporting period of 5 mins 
# 
ols=$1 # 'zooma' or 'bioportal'
if [ "$ols" == "zooma" ]; then
  search_str='ZOOMA Statistics'
else
  search_str='BioPortal Statistics'
fi
  
printf "speed (calls/min)\tfail rate\tcalls\tfails\n"
grep "${search_str}" logs/*.log \
| sed -r s/'.*throughput.* ([0-9]+) calls.*failed.* ([0-9,\.]+) .*'/'\1;\2'/ \
|(
total_calls=0
total_failed=0
while read line
do
  speed=$(echo $line | cut -f 1 -d ';')
  fail_rate=$(echo $line | cut -f 2 -d ';')

	# Compute total/failed calls, based on speed + sampling time and fail rate
  calls=$(($speed * 5)) 
  failed=$(bc <<< "scale=2;$calls * $fail_rate/100.0")
  printf "%s\t%s\t%s\t%s\n" $speed $fail_rate $calls $failed
  
  total_calls=$(($total_calls + $calls))
  total_failed=$(bc <<< "$total_failed + $failed")
done

total_fail_rate=$(bc <<< "scale=2;100.0 * $total_failed / $total_calls ")
echo ''
echo ''
echo "Total calls: $total_calls Total failed: $total_failed ($total_fail_rate %)"
)
