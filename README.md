# - Jenkins -

An Ubuntu based container built for running a Jenkins Continuous Integration server. Comes packaged with Logstash-Forwarder for log shipping, redpill - a bash script service monitor, and a few Jenkins groovy scripts meant to aid integration with Mesos.


##### Version Information:

* **Container Release:** 1.0.1
* **Mesos:** 0.23.0
* **Jenkins:**  1.609.3
* **Jenkins Mesos Plugin:** 0.8.0


##### Services Include:
* **[Jenkis](#jenkins)** - A well known java based Continuous Integration / Continuous Deployment service.
* **[Logstash-Forwarder](#logstash-forwarder)** - A lightweight log collector and shipper for use with [Logstash](https://www.elastic.co/products/logstash).
* **[Redpill](#redpill)** - A bash script and healthcheck for supervisord managed services. It is capable of running cleanup scripts that should be executed upon container termination.


---
---

### Index

* [Usage](#usage)
 * [Mesos Integration](#mesos-integration)
 * [Example Run Command](#example-run-command)
 * [Example Marathon App Definition](#example-marathon-app-definition)
* [Modification and Anatomy of the Project](#modification-and-anatomy-of-the-project)
* [Important Environment Variables](#important-environment-variables)
* [Service Configuration](#service-configuration)
 * [Jenkins](#jenkins)
   * [Jenkins Mesos Configuration Options](#jenkins-mesos-autoconfiguration-options)
 * [Logstash-Forwarder](#logstash-forwarder)
 * [Redpill](#redpill)
* [Troubleshooting](#troubleshooting)



---
---

### Usage

In a local environment without Mesos integration, nothing should have to be passed to the container to get going. A simple `docker run -d jenkins` should spawn Jenkins listening on port 8080.

For a production deployment, a bit more should be considered. Namely, storing the Jenkins configuration long term. Jenkins stores it's configuration information in `/var/lib/jenkins` (`$JENKINS_HOME`), this volume should either be mounted to the host or preconfigured via a script supplied by setting the `ENVIRONMENT_INIT` variable.

**Note:** If a volume is to be mounted; any Jenkins scripts or configs that are stored in `/usr/share/jenkins/ref` will not be run. If they should be; copy them to `$JENKINS_HOME` via script specified in `ENVRIONMENT_INIT`.

The script method is ideal to use in conjuction with something along the lines of the [SCM Sync Configuration Plugin](https://wiki.jenkins-ci.org/display/JENKINS/SCM+Sync+configuration+plugin) that will allow you to save your configs in git or svn.

Other than that, the only true minimum requirements for getting going in a production setting is to specify `ENVRIONMENT` as `production`, and set `JENKINS_JNLP_PORT` if you do not wish to use the default `8090`.   All other Jenkins related settings should be tuned to your environment. For further information regarding how to pass other Jenkins settings, please see the [Jenkins service](#jenkins) section.



#### Mesos Integration
To run Jenkins with Mesos integration, the container **MUST** be run with host networking and `LIBPROCESS_PORT` should be set to a different port than what was used for the mesos-slave itself or any other frameworks that might be scheduled on the same host (default port is `9000`).

To enable Jenkins-Mesos Autoconfiguration set `JENKINS_MESOS_AUTOCONF` to `enabled`. This will trigger a Jenkins init groovy script to disable any executors on the master and will either add a **NEW** cloud to the Jenkins server, or modify a few specific variables of every Mesos Cluster already defined. It **CANNOT** discern between different Mesos clusters.


For a full list of available options and their descriptions, please see the [Jenkins Mesos Autoconfiguration Options](#jenkins-mesos-autoconfiguration-options).


##### Example Run Command

```
#!/bin/bash
docker run -d --net=host \
-e ENVIRONMENT=production \
-e PARENT_HOST=$(hostname) \
-e JAVA_OPTS="-Xmx1024mb" \
-e LIBPROCESS_PORT=9400 \
-e JENKINS_LOG_FILE_THRESHOLD=WARNING \
-e JENKINS_LOG_STDOUT_THRESHOLD=WARNING \
-e JENKINS_MESOS_AUTOCONF=enabled \
-e JENKINS_MESOS_MASTER=zk://10.10.0.11:2181,10.10.0.12:2181,10.10.0.13:2181/mesos \
-e JENKINS_MESOS_SLAVE_1_LABEL=mesos \
-e JENKINS_MESOS_SLAVE_1_DOCK_IMG=jenkins-build-base \
-e JENKINS_MESOS_SLAVE_2_LABEL=mesos-docker \
-e JENKINS_MESOS_SLAVE_2_DOCK_IMG=jenkins-build-base \
-e JENKINS_MESOS_SLAVE_2_VOL_1=/usr/bin/docker::/usr/bin/docker::ro \
-e JENKINS_MESOS_SLAVE_2_VOL_2=/var/run/docker.sock::/var/run/docker.sock::rw \
-e JENKINS_MESOS_SLAVE_2_ADD_URIS_1=file:///docker.tar.gz::false::false \
jenkins
```

##### Example Marathon App Definition

```
{
    "id": "/jenkins",
    "instances": 1,
    "cpus": 1,
    "mem": 512,
    "container": {
        "type": "DOCKER",
        "docker": {
            "image": "registry.address/mesos/jenkins",
            "network": "HOST"
        }
    },
    "env": {
        "ENVIRONMENT": "production",
        "JAVA_OPTS": "-Xmx1024mb",
        "LIBPROCESS_PORT": "9400",
        "JENKINS_LOG_FILE_THRESHOLD": "WARNING",
        "JENKINS_LOG_STDOUT_THRESHOLD": "WARNING",
        "JENKINS_MESOS_AUTOCONF": "enabled",
        "JENKINS_MESOS_MASTER": "zk://10.10.0.11:2181,10.10.0.12:2181,10.10.0.13:2181/mesos",
        "JENKINS_MESOS_SLAVE_1_LABEL": "mesos-docker",
        "JENKINS_MESOS_SLAVE_1_DOCK_IMG": "jenkins-build-base",
        "JENKINS_MESOS_SLAVE_1_VOL_1": "/usr/bin/docker::/usr/bin/docker::ro",
        "JENKINS_MESOS_SLAVE_1_VOL_2": "/var/run/docker.sock::/var/run/docker.sock::rw",
        "JENKINS_MESOS_SLAVE_1_ADD_URIS_1": "file:///docker.tar.gz::false::false",
        "JENKINS_MESOS_SLAVE_2_LABEL": "mesos",
        "JENKINS_MESOS_SLAVE_2_DOCK_IMG": "jenkins-build-base"
    },
    "uris": [
        "file:///docker.tar.gz"
    ]
}
```

* **Note:** The example assumes a v1.6+ version of docker or a v2 version of the docker registry. For information on using an older version or connecting to a v1 registry, please see the [private registry](https://mesosphere.github.io/marathon/docs/native-docker-private-registry.html) section of the Marathon documentation.


---
---


### Modification and Anatomy of the Project

**File Structure**
The directory `skel` in the project root maps to the root of the filesystem once the container is built. Files and folders placed there will map to their corrisponding location within the container.

**Init**
The init script (`./init.sh`) found at the root of the directory is the entry process for the container. It's role is to simply set specific environment variables and modify any subsiquently required configuration files.

**Jenkins**
Jenkins configs are stored in two locations. An 'initial seed' of configs stored in `/usr/share/jenkins/ref` are then copied to their final location in `/var/lib/jenkins`.

**Supervisord**
All supervisord configs can be found in `/etc/supervisor/conf.d/`. Services by default will redirect their stdout to `/dev/fd/1` and stderr to `/dev/fd/2` allowing for service's console output to be displayed. Most applications can log to both stdout and their respecively specified log file.

In some cases (such as with zookeeper), it is possible to specify different logging levels and formats for each location.

**Logstash-Forwarder**
The Logstash-Forwarder binary and default configuration file can be found in `/skel/opt/logstash-forwarder`. It is ideal to bake the Logstash Server certificate into the base container at this location. If the certificate is called `logstash-forwarder.crt`, the default supplied Logstash-Forwarder config should not need to be modified, and the server setting may be passed through the `SERICE_LOGSTASH_FORWARDER_ADDRESS` environment variable.

In practice, the supplied Logstash-Forwarder config should be used as an example to produce one tailored to each deployment.

---
---

### Important Environment Variables

Below is the minimum list of variables to be aware of when deploying the Jenkins container.

#### Defaults

| **Variable**                      | **Default**                                             |
|-----------------------------------|---------------------------------------------------------|
| `ENVIRONMENT_INIT`                |                                                         |
| `APP_NAME`                        | `jenkins`                                               |
| `ENVIRONMENT`                     | `local`                                                 |
| `PARENT_HOST`                     | `unknown`                                               |
| `LIBPROCESS_IP`                   |                                                         |
| `LIBPROCESS_PORT`                 | `9000`                                                  |
| `JAVA_OPTS`                       |                                                         |
| `JENKINS_HTTP_LISTEN_ADDRESS`     | `0.0.0.0`                                               |
| `JENKINS_HTTP_PORT`               | `8080`                                                  |
| `JENKINS_JNLP_PORT`               | `8090`                                                  |
| `JENKINS_LOG_FILE_PATTERN`        | `/var/log/jenkins/jenkins.log`                          |
| `JENKINS_LOG_FILE_THRESHOLD`      |                                                         |
| `JENKINS_LOG_STDOUT_THRESHOLD`    |                                                         |
| `SERVICE_LOGSTASH_FORWARDER`      |                                                         |
| `SERVICE_LOGSTASH_FORWARDER_CONF` | `/opt/logstash-forwarer/jenkins.conf`                   |
| `SERVICE_REDPILL`                 |                                                         |
| `SERVICE_REDPILL_MONITOR`         | `jenkins`                                               |

##### Description

* `ENVIRONMENT_INIT` - If set, and the file path is valid. This will be sourced and executed before **ANYTHING** else. Useful if supplying an environment file or need to query a service such as consul to populate other variables.

* `APP_NAME` - A brief description of the container. If Logstash-Forwarder is enabled, this will populate the `app_name` field in the Logstash-Forwarder configuration file.

* `ENVIRONMENT` - Sets defaults for several other variables based on the current running environment. Please see the [environment](#environment) section for further information. If logstash-forwarder is enabled, this value will populate the `environment` field in the logstash-forwarder configuration file.

* `PARENT_HOST` - The name of the parent host. If `HOST` is found as an environmet variable, and `PARENT_HOST` is not set. Init will automatically set `PARENT_HOST` equal to host (this would be the case by default in marathon). If Logstash-Forwarder is enabled, this will populate the `parent_host` field in the Logstash-Forwarder configuration file.

* `LIBPROCESS_IP` - The IP used to communicate with Mesos.

* `LIBPROCESS_PORT` - The port that will be used for communicating with Mesos.

* `JAVA_OPTS` - The Java environment variables that will be passed to Jenkins at runtime. Generally used for adjusting memory allocation (`-Xms` and `-Xmx`).

* `SERVICE_LOGSTASH_FORWARDER` - Enables or disables the Logstash-Forwarder service. Set automatically depending on the `ENVIRONMENT`. See the Environment section below.  (**Options:** `enabled` or `disabled`)

* `SERVICE_LOGSTASH_FORWARDER_CONF` - The path to the logstash-forwarder configuration.

* `SERVICE_REDPILL` - Enables or disables the Redpill service. Set automatically depending on the `ENVIRONMENT`. See the Environment section below.  (**Options:** `enabled` or `disabled`)

* `SERVICE_REDPILL_MONITOR` - The name of the supervisord service(s) that the Redpill service check script should monitor.

---


#### Environment

* `local` (default)

| **Variable**                   | **Default**                |
|--------------------------------|----------------------------|
| `JAVA_OPTS`                    | `-Xmx256m`                 |
| `JENKINS_LOG_FILE_THRESHOLD`   | `WARNING`                  |
| `JENKINS_LOG_STDOUT_THRESHOLD` | `WARNING`                  |
| `SERVICE_LOGSTASH_FORWARDER`   | `disabled`                 |
| `SERVICE_REDPILL`              | `enabled`                  |


* `prod`|`production`|`dev`|`development`

| **Variable**                   | **Default** |
|--------------------------------|-------------|
| `JENKINS_LOG_FILE_THRESHOLD`   | `WARNING`   |
| `JENKINS_LOG_STDOUT_THRESHOLD` | `WARNING`   |
| `SERVICE_LOGSTASH_FORWARDER`   | `enabled`   |
| `SERVICE_REDPILL`              | `enabled`   |


* `debug`

| **Variable**                   | **Default** |
|--------------------------------|-------------|
| `JENKINS_LOG_FILE_THRESHOLD`   | `FINEST`    |
| `JENKINS_LOG_STDOUT_THRESHOLD` | `FINEST`    |
| `SERVICE_LOGSTASH_FORWARDER`   | `disabled`  |
| `SERVICE_REDPILL`              | `disabled`  |


---
---

### Service Configuration

---

### Jenkins


#### Jenkins Command Line Parameters

Any of the Jenkins commandline argument may be passed as an environment variable. The init script will pick up on anything prefixed with `JENKINS_` and assume it is to be interrpretted as a jenkins command line paramter. In addition to starting with the prefix, an `_` should be used between any instance where there would be any word change or use of a capital letter. e.g. `--httpListenAddress` would become `JENKINS_HTTP_LISTEN_ADDRESS`. For an accurate list of available options; execute the following: `docker run --rm jenkins java -jar /usr/share/jenkins/jenkins.war --help`.

**Note:** There are a few exclusions to the above rule, these are things that the script itself interprets or expects to have an groovy init script handle. These options include: `JENKINS_ARGS`, `JENKINS_HOME`, `JENKINS_JNLP`, `JENKINS_LOG_FILE_*`, `JENKINS_LOG_STDOUT_*`, and `JENKINS_MESOS`.


In addition to the above, there are two additional `JAVA_OPTS` that will always be passed during startup:

* `-Djava.awt.headless=true`
* `-Djava.util.logging.config.file=$JENKINS_HOME/logging.properties`

These enable headless mode, and tells java to use the logging settings found in `$JENKINS_HOME/logging.properties`.


#### Jenkins Environment Variables

##### Defaults

| **Variable**                   | **Default**                    |
|--------------------------------|--------------------------------|
| `JAVA_OPTS`                    |                                |
| `JENKINS_ARGS`                 |                                |
| `JENKINS_HTTP_LISTEN_ADDRESS`  | `0.0.0.0`                      |
| `JENKINS_HTTP_PORT`            | `8080`                         |
| `JENKINS_JNLP_PORT`            | `8090`                         |
| `JENKINS_LOG_FILE_PATTERN`     | `/var/log/jenkins/jenkins.log` |
| `JENKINS_LOG_FILE_THRESHOLD`   |                                |
| `JENKINS_LOG_STDOUT_THRESHOLD` |                                |
| `SERVICE_JENKINS_CMD`          |                                |

##### Description

* `JAVA_OPTS` - The Java environment variables that will be passed to Jenkins at runtime. Generally used for adjusting memory allocation (`-Xms` and `-Xmx`).

* `JENKINS_ARGS` - Additional commandline options that will be passed to jenkins at startup (alternative to supplying them as environment variables).

* `JENKINS_HTTP_PORT` - The port Jenkins will listen in on for incoming http connections.

* `JENKINS_JNLP_PORT` - The port that  Jenkins will listen in on for JNLP connections.

* `JENKINS_LOG_FILE_PATTERN` - The full path to the log file name to be used. **Note:** Can use patterns in file path. For more information see the java docs: [java.util.logging.FileHandler](http://docs.oracle.com/javase/7/docs/api/java/util/logging/FileHandler.html).

* `JENKINS_LOG_FILE_THRESHOLD` - The log level to be used with the file logger. Jenkins uses java.util.logging for it's logging system. The available log levels include: `ALL`, `CONFIG`, `FINE`, `FINER`, `FINEST`, `INFO`, `OFF`, `SEVERE`, `WARNING`.

* `JENKINS_LOG_STDOUT_THRESHOLD` - The log level to be used with stdout/stderr.  Jenkins uses java.util.logging for it's logging system. The available log levels include: `ALL`, `CONFIG`, `FINE`, `FINER`, `FINEST`, `INFO`, `OFF`, `SEVERE`, `WARNING`.

* `SERVICE_JENKINS_CMD` - The command that is passed to supervisor. If overriding, must be an escaped python string expression. Please see the [Supervisord Command Documentation](http://supervisord.org/configuration.html#program-x-section-settings) for further information. 

---

#### Jenkins Mesos Autoconfiguration Options

##### WARNING:
If you take the container as is, and only supply the Mesos settings; then any jobs added will fail to run. It will **NOT** schedule and a Null Pointer Exception Error will be thrown. The solution to this is simple. Start the Jenkins container then go to `Manage Jenkins` -> `Configure System`, change any setting, and save the configuration. The act of saving the configuration will fix the issue, and it should be ready to run jobs.

**Note:** This will not occur if Jenkins has already been configured (e.g. loading configs from SCM). It is simply a part of a vanilla bootstrap process.


##### Variables with '###' in their name
Multiple Mesos slave images may be definied. Settings for them are grouped together based on the first `###` in the variable name. e.g. `JENKINS_MESOS_SLAVE_1_LABEL=mesos` and `JENKINS_MESOS_SLAVE_1_DOCK_IMG=jenkins-build-base` will apply to the same configuration. In instances where there is a `###` at the end of the variable name, multiple instances of that variable may be supplied. e.g. `JENKINS_MESOS_SLAVE_2_VOL_1=/usr/bin/docker::/usr/bin/docker:ro` and `JENKINS_MESOS_SLAVE_2_VOL_2=/var/run/docker.sock::/var/run/docker.sock::rw` will be two volumes mounted to the same container.

**Note:** Add/Modify applies to variable availability either when adding a new Mesos Cloud or Modifying one in place.

#### Defaults

| **Variable**                                | **Add/Modify** | **Default**                                                       |
|---------------------------------------------|----------------|-------------------------------------------------------------------|
| `JENKINS_MESOS_AUTOCONF`                    | Both           | `enabled`                                                         |
| `JENKINS_MESOS_MASTER`                      | Both           |                                                                   |
| `JENKINS_MESOS_DESCRIPTION`                 | Both           |                                                                   |
| `JENKINS_MESOS_FRAMEWORK_NAME`              | Both           | `Jenkins Scheduler`                                               |
| `JENKINS_MESOS_SLAVES_USER`                 | Both           | `jenkins`                                                         |
| `JENKINS_MESOS_PRINCIPAL`                   | Both           | `jenkins`                                                         |
| `JENKINS_MESOS_SECRET`                      | Both           |                                                                   |
| `JENKINS_MESOS_CHECKPOINT`                  | Add            | `false`                                                           |
| `JENKINS_MESOS_ON_DEMAND`                   | Both           | `true`                                                            |
| `JENKINS_MESOS_URL`                         | Both           |                                                                   |
| `JENKINS_MESOS_SLAVE_###_LABEL`             | Add            | `mesos-${###}`                                                    |
| `JENKINS_MESOS_SLAVE_###_CPU`               | Add            | `0.1`                                                             |
| `JENKINS_MESOS_SLAVE_###_MEM`               | Add            | `512`                                                             |
| `JENKINS_MESOS_SLAVE_###_MAX_EXEC`          | Add            | `2`                                                               |
| `JENKINS_MESOS_SLAVE_###_EXEC_CPU`          | Add            | `1`                                                               |
| `JENKINS_MESOS_SLAVE_###_EXEC_MEM`          | Add            | `128`                                                             |
| `JENKINS_MESOS_SLAVE_###_RFS_ROOT`          | Add            | `jenkins`                                                         |
| `JENKINS_MESOS_SLAVE_###_IDLE_TERM`         | Add            | `3`                                                               |
| `JENKINS_MESOS_SLAVE_###_SLAVE_ATTRIB`      | Add            |                                                                   |
| `JENKINS_MESOS_SLAVE_###_JVM_ARGS`          | Add            | `-Xms16m -XX:+UseConcMarkSweepGC -Djava.net.preferIPv4Stack=true` |
| `JENKINS_MESOS_SLAVE_###_JNLP_ARGS`         | Add            |                                                                   |
| `JENKINS_MESOS_SLAVE_###_DOCK_IMG`          | Add            |                                                                   |
| `JENKINS_MESOS_SLAVE_###_PRIV_MODE`         | Add            | `false`                                                           |
| `JENKINS_MESOS_SLAVE_###_FORCE_PULL`        | Add            | `false`                                                           |
| `JENKINS_MESOS_SLAVE_###_CMD_SHELL`         | Add            | `false`                                                           |
| `JENKINS_MESOS_SLAVE_###_CMD_SHELL_COMMAND` | Add            |                                                                   |
| `JENKINS_MESOS_SLAVE_###_NETWORKING`        | Add            | `BRIDGE`                                                          |
| `JENKINS_MESOS_SLAVE_###_PORTS_###`         | Add            |                                                                   |
| `JENKINS_MESOS_SLAVE_###_PARAM_###`         | Add            |                                                                   |
| `JENKINS_MESOS_SLAVE_###_VOL_###`           | Add            |                                                                   |
| `JENKINS_MESOS_SLAVE_###_ADD_URIS_###`      | Add            |                                                                   |


##### Description

* `JENKINS_MESOS_AUTOCONF` - Enables or disables The Jenkins Mesos plugin autoconfiguration. (**Options:** `enabled` or `disabled`)

* `JENKINS_MESOS_MASTER` - A comma delimited list of Mesos Masters in the form of host:port or the zk url used for Mesos.

* `JENKINS_MESOS_DESCRIPTION` - A description of the Mesos Cluster

* `JENKINS_MESOS_FRAMEWORK_NAME` - The name of the Jenkins Framework that registers with Mesos.

* `JENKINS_MESOS_SLAVES_USER` - The user that tasks will be managed under on the slaves.

* `JENKINS_MESOS_PRINCIPAL` - The user principal that is registered with Mesos.

* `JENKINS_MESOS_SECRET` - A password or secret required for framework authorization.

* `JENKINS_MESOS_CHECKPOINT` - Enables framework checkpointing and recovery. **Note:** Checkpointing **MUST** be enabled on slaves first. (**Options:** `true` or `false`)

* `JENKINS_MESOS_ON_DEMAND` - Registers the Jenkins Framework with Mesos **ONLY** when there is jobs to run. (**Options:** `true` or `false`)

* `JENKINS_MESOS_URL` - The url of the jenkins instance. Useful when Jenkins is running behind a reverse proxy.

* `JENKINS_MESOS_SLAVE_###_LABEL` - A label used within jenkins to identify a specific slave executor to use for a job.

* `JENKINS_MESOS_SLAVE_###_CPU` - The Jenkin's slaves CPU.

* `JENKINS_MESOS_SLAVE_###_MEM` - The Jenkins slave's Memory (in MB).

* `JENKINS_MESOS_SLAVE_###_MAX_EXEC` - The maximum number of executors per slave.

* `JENKINS_MESOS_SLAVE_###_EXEC_CPU` - The CPU allocated to the Jenkins executor.

* `JENKINS_MESOS_SLAVE_###_EXEC_MEM` - The Memory allocated to the Jenkins executor (in MB).

* `JENKINS_MESOS_SLAVE_###_RFS_ROOT` - The remote file system root to be used on the slaves.

* `JENKINS_MESOS_SLAVE_###_IDLE_TERM` - The amount of minutes before a jenkins slave is terminated.

* `JENKINS_MESOS_SLAVE_###_SLAVE_ATTRIB` - a JSON string of Mesos selection attributes.

* `JENKINS_MESOS_SLAVE_###_JVM_ARGS` - The settings passed to the slave agent's JVM.

* `JENKINS_MESOS_SLAVE_###_JNLP_ARGS` - Additional JNLP arguments to be supplied to the slave e.g. `-jnlpCredentials`.

* `JENKINS_MESOS_SLAVE_###_DOCK_IMG` - The docker image that should be used for this specific slave.

* `JENKINS_MESOS_SLAVE_###_PRIV_MODE` - Should the container be run in privleged mode (**Options:** `true` or `false`).

* `JENKINS_MESOS_SLAVE_###_FORCE_PULL` - Forces Docker to pull the image, even if it exists locally. (**Options:** `true` or `false`)

* `JENKINS_MESOS_SLAVE_###_CMD_SHELL` - Override the standard command used to launch the Jenkins slave agent within a container. (**Options:** `true` or `false`)

* `JENKINS_MESOS_SLAVE_###_CMD_SHELL_COMMAND` - The command used to launch the Jenkins slave agent if `JENKINS_MEOSS_SLAVE_###_CMD_SHELL` is set to `true`.

* `JENKINS_MESOS_SLAVE_###_NETWORKING` - The type of network used when launching the Jenkins slave container (**Options:** `BRIDGE` or `HOST`)

* `JENKINS_MESOS_SLAVE_###_PORTS_###` - A port to forward to the Jenkins slave container. Supplied in the form of `<hostPort>::<containerPort>::<protocol>`

* `JENKINS_MESOS_SLAVE_###_PARAM_###` - Additional parameters to be passed to the Jenkins slave container on launch. Supplied in the form of `<key>::<value>`.

* `JENKINS_MESOS_SLAVE_###_VOL_###` - Volume to mount to the Jenkins slave container. Supplied in the form of `<hostPath>::<containerPath>::<rw|ro>`

* `JENKINS_MESOS_SLAVE_###_ADD_URIS_###` - Any additional URI's to pass to the Jenkins slave container. Supplied in the form of `<uri>::<executable - true|false>::<extract true|false>`. **Note:** Ideal for docker registry credentials. 



---

### Logstash-Forwarder

Logstash-Forwarder is a lightweight application that collects and forwards logs to a logstash server endpoint for further processing. For more information see the [Logstash-Forwarder](https://github.com/elastic/logstash-forwarder) project.


#### Logstash-Forwarder Environment Variables

##### Defaults

| **Variable**                         | **Default**                                                                            |
|--------------------------------------|----------------------------------------------------------------------------------------|
| `SERVICE_LOGSTASH_FORWARDER`         |                                                                                        |
| `SERVICE_LOGSTASH_FORWARDER_CONF`    | `/opt/logstash-forwarder/jenkins.conf`                                               |
| `SERVICE_LOGSTASH_FORWARDER_ADDRESS` |                                                                                        |
| `SERVICE_LOGSTASH_FORWARDER_CERT`    |                                                                                        |
| `SERVICE_LOGSTASH_FORWARDER_CMD`     | `/opt/logstash-forwarder/logstash-fowarder -cofig="${SERVICE_LOGSTASH_FOWARDER_CONF}"` |


##### Description

* `SERVICE_LOGSTASH_FORWARDER` - Enables or disables the Logstash-Forwarder service. Set automatically depending on the `ENVIRONMENT`. See the Environment section.  (**Options:** `enabled` or `disabled`)

* `SERVICE_LOGSTASH_FORWARDER_CONF` - The path to the logstash-forwarder configuration.

* `SERVICE_LOGSTASH_FORWARDER_ADDRESS` - The address of the Logstash server.

* `SERVICE_LOGSTASH_FORWARDER_CERT` - The path to the Logstash-Forwarder server certificate.

* `SERVICE_LOGSTASH_FORWARDER_CMD` - The command that is passed to supervisor. If overriding, must be an escaped python string expression. Please see the [Supervisord Command Documentation](http://supervisord.org/configuration.html#program-x-section-settings) for further information.

---


### Redpill

Redpill is a small script that performs status checks on services managed through supervisor. In the event of a failed service (FATAL) Redpill optionally runs a cleanup script and then terminates the parent supervisor process.

#### Redpill Environment Variables

##### Defaults

| **Variable**               | **Default** |
|----------------------------|-------------|
| `SERVICE_REDPILL`          |             |
| `SERVICE_REDPILL_MONITOR`  | `jenkins`   |
| `SERVICE_REDPILL_INTERVAL` |             |
| `SERVICE_REDPILL_CLEANUP`  |             |
| `SERVICE_REDPILL_CMD`      |             |


##### Description

* `SERVICE_REDPILL` - Enables or disables the Redpill service. Set automatically depending on the `ENVIRONMENT`. See the Environment section.  (**Options:** `enabled` or `disabled`)

* `SERVICE_REDPILL_MONITOR` - The name of the supervisord service(s) that the Redpill service check script should monitor. 

* `SERVICE_REDPILL_INTERVAL` - The interval in which Redpill polls supervisor for status checks. (Default for the script is 30 seconds)

* `SERVICE_REDPILL_CLEANUP` - The path to the script that will be executed upon container termination.

* `SERVICE_REDPILL_CMD` - The command that is passed to supervisor. It is dynamically built from the other redpill variables. If overriding, must be an escaped python string expression. Please see the [Supervisord Command Documentation](http://supervisord.org/configuration.html#program-x-section-settings) for further information.


##### Redpill Script Help Text

```
root@c90c98ae31e1:/# /opt/scripts/redpill.sh --help
Redpill - Supervisor status monitor. Terminates the supervisor process if any specified service enters a FATAL state.

-c | --cleanup    Optional path to cleanup script that should be executed upon exit.
-h | --help       This help text.
-i | --inerval    Optional interval at which the service check is performed in seconds. (Default: 30)
-s | --service    A comma delimited list of the supervisor service names that should be monitored.
```

---
---

### Troubleshooting

In the event of an issue, the `ENVIRONMENT` variable can be set to `debug`.  This will stop the container from shipping logs and prevent it from terminating if one of the services enters a failed state. It will also default the logging level for both stdout and the file to `DEBUG`.
