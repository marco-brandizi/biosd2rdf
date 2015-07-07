# Estimates the no of failed ZOOMA calls, using log messages
# These are generated by StatsZOOMASearchFilter, as long as it's used and enabled with a reporting period of 5 mins 
# 
printf "speed\tfail rate\tfails\n"
grep 'ZOOMA Statistics, searchZOOMA' logs/*.log \
| sed -r s/'.*throughput.* ([0-9]+) calls.*failed.* ([0-9,\.]+) .*'/'\1;\2'/ \
|(sum=0
while read line
do
  speed=$(echo $line | cut -f 1 -d ';')
  fail_rate=$(echo $line | cut -f 2 -d ';')

	# 
  failed=$(bc <<< "scale=2;$speed * 5 * $fail_rate/100.0")
  printf "%s\t%s\t%s\n" $speed $fail_rate $failed
  sum=$(bc <<< "$sum + $failed")
done

echo ''
echo ''
echo 'Total failed:' $sum)