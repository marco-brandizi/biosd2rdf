
cd "$(dirname $0)"
. ./_get_ver.sh

ds_dir=/nfs/production2/linked-data/biosamples/$version

cd "$ds_dir"

owlapi_markup='Generated by the OWL API'
trailer_count=$(tail biosd_*.ttl | grep --count "$owlapi_markup")
file_count=$(ls biosd_*.ttl | wc -l)

echo
echo "Files correctly trailed: $trailer_count"
echo "Total files: $file_count"
echo

xcode=0
if [ "$trailer_count" == "$file_count" ]; then

  echo "Export seems SUCCESSFUL!"
  
else
  
  echo "Something went WRONG, files that seem corrupted:"
  
	for i in biosd_[0-9]*.ttl
  do 
    if ! tail $i | grep --files-without-match --quiet "$owlapi_markup"; then
      printf "$i\t"
    fi
  done  
    
  xcode=1
fi

echo
echo

exit $xcode

