################################################################################
# jenkins:1.0.2
# Date: 10/16/2015
# Jenkins Version: 1.609.3 (LTS Release)
# Mesos Version: 0.23.1-0.2.61.ubuntu1404
#
# Description:
# Jenkins CI/CD container. Packages can be added by appending them to the
# $PLUGIN_DEFS script located at /opt/scripts/plugins.def. They use the format
# <plugin_name>:<plugin_version> 
# just using <plugin_name> will automatically used the latest version of the
# plugin available.
################################################################################

FROM mrbobbytables/mesos-base:1.0.1
MAINTAINER Bob Killen / killen.bob@gmail.com / @mrbobbytables


ENV VERSION_JENKINS=1.609.3        \
    JENKINS_HOME=/var/lib/jenkins  \
    PLUGIN_DEFS=/opt/scripts/plugins.def

RUN mkdir -p /usr/share/jenkins        \
 && mkdir -p /var/log/jenkins          \
 && mkdir -p /var/lib/jenkins/plugins  \
 && groupadd -g 989 jenkins            \
 && useradd -d $JENKINS_HOME -u 989 -g 989 -s /bin/bash jenkins  \
 && wget -P /usr/share/jenkins http://mirrors.jenkins-ci.org/war-stable/$VERSION_JENKINS/jenkins.war

COPY ./skel /

# mimic the official jenkins container and use ref to house scripts etc.
# sleep 1 to get around 'text file busy' error.
RUN chmod +x ./init.sh                                   \
 && chmod +x /opt/scripts/fetch-jenkins-plugins.sh       \
 && cp -R /usr/share/jenkins/ref/* /var/lib/jenkins      \
 && sleep 1                                              \
 && /opt/scripts/fetch-jenkins-plugins.sh $PLUGIN_DEFS   \
 && chown -R jenkins:jenkins /usr/share/jenkins          \
 && chown -R jenkins:jenkins /var/lib/jenkins            \
 && chown -R jenkins:jenkins /var/log/jenkins

#jenkins-web, JNLP, LIBPROCESS
expose 8080 8090 9000

CMD ["./init.sh"]
