#!/bin/bash
docker run -d --net=host \
-e ENVIRONMENT=production \
-e PARENT_HOST=$(hostname) \
-e JAVA_OPTS="-Xmx1024mb" \
-e LIBPROCESS_PORT=9400 \
-e JENKINS_LOG_FILE_THRESHOLD=WARNING \
-e JENKINS_LOG_STDOUT_THRESHOLD=WARNING \
-e JENKINS_MESOS_AUTOCONF=enabled \
-e JENKINS_MESOS_MASTER="zk://10.10.0.11:2181,10.10.0.12:2181,10.10.0.13:2181/mesos" \
-e JENKINS_MESOS_SLAVE_1_LABEL=mesos-docker \
-e JENKINS_MESOS_SLAVE_1_DOCK_IMG=jenkins-build-base \
-e JENKINS_MESOS_SLAVE_1_VOL_1=/usr/bin/docker::/usr/bin/docker::ro \
-e JENKINS_MESOS_SLAVE_1_VOL_2=/var/run/docker.sock::/var/run/docker.sock::rw \
-e JENKINS_MESOS_SLAVE_1_ADD_URIS_1=file:///docker.tar.gz::false::false \
-e JENKINS_MESOS_SLAVE_2_LABEL=mesos \
-e JENKINS_MESOS_SLAVE_2_DOCK_IMG=jenkins-build-base \
jenkins
