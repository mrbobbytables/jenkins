#!/bin/bash

if [[ ! -f $1 ]]; then
  echo "No plugin list specified."
  exit
fi

plugin_dir=$JENKINS_HOME/plugins
mkdir -p "$plugin_dir"

jenkins_dl=https://updates.jenkins-ci.org/download/plugins

while read -r plugin_raw
do
if echo "$plugin_raw" | grep -E -q '[^#|\s*$]' ; then
  plugin=(${plugin_raw//:/ })
  if [[ -z ${plugin[1]} ]]; then
    plugin[1]="latest"
  fi
  echo "Fetching plugin: ${plugin[0]}:${plugin[1]}"
  wget -P "$plugin_dir" "$jenkins_dl/${plugin[0]}/${plugin[1]}/${plugin[0]}.hpi"
fi
done < "$1"
