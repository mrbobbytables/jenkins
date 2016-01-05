#!/bin/bash

##### Sample environment init script #####
# PORT0 = Jenkins HTTP Web Port
# PORT1 = Jenkins JNLP Port
# PORT2 = libprocess bind port
##########################################


#seed $JENKINS_HOME
cp -Ru /usr/share/jenkins/ref/* "$JENKINS_HOME"

if [[ ! -d "$JENKINS_HOME/plugins" ]]; then
  /opt/scripts/fetch-jenkins-plugins.sh "$PLUGIN_DEFS"
fi

chown -R jenkins:jenkins "$JENKINS_HOME"
chown -R jenkins:jenkins /usr/share/jenkins
chown -R jenkins:jenkins /var/log/jenkins


local_ip="$(ip addr show eth0 | grep -m 1 -P -o '(?<=inet )[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}')"

export JENKINS_HTTP_PORT="$PORT0"
export JENKINS_JNLP_PORT="$PORT1"
export LIBPROCESS_IP="$local_ip"
export LIBPROCESS_PORT="$PORT2"
export LIBPROCESS_ADVERTISE_IP="$HOST"
export LIBPROCESS_ADVERTISE_PORT="$PORT2"


echo "[$(date)][env-init][JENKINS_HTTP_PORT] $PORT0"
echo "[$(date)][env-init][JENKINS_JNLP_PORT] $PORT1"
echo "[$(date)][env-init][LIBPROCESS_IP] $local_ip"
echo "[$(date)][env-init][LIBPROCESS_PORT] $PORT2"
echo "[$(date)][env-init][LIBPROCESS_ADVERTISE_IP] $HOST"
echo "[$(date)][env-init][LIBPROCESS_ADVERTISE_PORT] $PORT2"
