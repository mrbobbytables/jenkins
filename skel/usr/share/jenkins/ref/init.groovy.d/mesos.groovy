#!/usr/bin/env groovy
/*

Fairly self explanatory if you look at the options in the UI.
Main thing to note is the JENKINS_MESOS_SLAVE_<number>. Anything with the same
number will be associated together option wise.

For things like JENKINS_MESOS_SLAVE_<number>_VOL_<number> The trailing
number just signifies more than one.
--Available Environment Variables --
JENKINS_MESOS_AUTOCONF            - set to 'enable' to continue with autoconfig.
JENKINS_MESOS_MASTER
JENKINS_MESOS_DESCRIPTION
JENKINS_MESOS_FRAMEWORK_NAME
JENKINS_MESOS_SLAVES_USER
JENKINS_MESOS_PRINCIPAL
JENKINS_MESOS_SECRET
JENKINS_MESOS_CHECKPOINT
JENKINS_MESOS_ON_DEMAND
JENKINS_MESOS_URL
JENKINS_MESOS_SLAVE_<number>_LABEL
JENKINS_MESOS_SLAVE_<number>_CPU
JENKINS_MESOS_SLAVE_<number>_MEM
JENKINS_MESOS_SLAVE_<number>_MAX_EXEC
JENKINS_MESOS_SLAVE_<number>_EXEC_CPU
JENKINS_MESOS_SLAVE_<number>_EXEC_MEM
JENKINS_MESOS_SLAVE_<number>_RFS_ROOT
JENKINS_MESOS_SLAVE_<number>_IDLE_TERM
JENKINS_MESOS_SLAVE_<number>_SLAVE_ATTRIB
JENKINS_MESOS_SLAVE_<number>_JVM_ARGS
JENKINS_MESOS_SLAVE_<number>_JNLP_ARGS
JENKINS_MESOS_SLAVE_<number>_DOCK_IMG
JENKINS_MESOS_SLAVE_<number>_PRIV_MODE
JENKINS_MESOS_SLAVE_<number>_FORCE_PULL
JENKINS_MESOS_SLAVE_<number>_CMD_SHELL
JENKINS_MESOS_SLAVE_<number>_CMD_SHELL_COMMAND
JENKINS_MESOS_SLAVE_<number>_NETWORKING
JENKINS_MESOS_SLAVE_<number>_PORTS_<number>        - hostPort::containerPort::protocol
JENKINS_MESOS_SLAVE_<number>_PARAM_<number>        - key::value
JENKINS_MESOS_SLAVE_<number>_VOL_<number>          - hostPath::containerPath::[rw|ro]
JENKINS_MESOS_SLAVE_<number>_ADD_URIS_<number>     - uri::<executable - true|false>::<extract - true|false>

Note: If updating a configuration, only the below list is available:
JENKINS_MESOS_MASTER
JENKINS_MESOS_DESCRIPTION
JENKINS_MESOS_FRAMEWORK_NAME
JENKINS_MESOS_SLAVES_USER
JENKINS_MESOS_PRINCIPAL
JENKINS_MESOS_SECRET
JENKINS_MESOS_ON_DEMAND
JENKINS_MESOS_URL

It will also update ALL MesosCloud configurations with the above. It cannot
discern individual ones.

*/

import hudson.model.*
import jenkins.model.*
import org.joda.time.DateTime
import org.jenkinsci.plugins.mesos.*


def addMesosCloud(cloudList, env) {

  println "[${DateTime.now()}][mesos.groovy]  No pre-existing Mesos Cloud configuration found. Adding new config."

  List<MesosSlaveInfo.ContainerInfo> slaveContainers = new ArrayList<MesosSlaveInfo.ContainerInfo>()
  def numContainers = []

  env.keySet().grep(~/JENKINS_MESOS_SLAVE_([0-9]{1,3})_.*/).each {
    def matcher = it =~ "[0-9]{1,3}"
    numContainers.add(matcher[0])
  }


  for (i in numContainers.unique().sort()) {
    List<MesosSlaveInfo.URI> slaveUris = new ArrayList<MesosSlaveInfo.URI>()
    List<MesosSlaveInfo.Volume> slaveVols = new ArrayList<MesosSlaveInfo.Volume>()
    List<MesosSlaveInfo.Parameter> slaveParams = new ArrayList<MesosSlaveInfo.Parameter>()
    List<MesosSlaveInfo.PortMapping> slavePorts =  new ArrayList<MesosSlaveInfo.PortMapping>()


    env.keySet().grep(~/JENKINS_MESOS_SLAVE_${i}_ADD_URIS_[0-9]{1,3}/).sort().each {
      def uri = env["${it}"].trim() =~ /(.*)::(true|false)::(true|false)/
      if (uri.matches()) {
        def addUri = new MesosSlaveInfo.URI(
          value=uri[0][1],
          executable=uri[0][2].toBoolean(),
          extract=uri[0][3].toBoolean()
        )
        slaveUris.add(addUri)
      } else {
        println "[${DateTime.now()}][mesos.groovy]  Error processing uri: ${it} -- ${env[it]}"
      }
    }
    if (slaveUris.isEmpty()) {slaveUris = null}


    env.keySet().grep(~/JENKINS_MESOS_SLAVE_${i}_VOL_[0-9]{1,3}/).sort().each {
      def vol = env["${it}"].trim() =~ /(.*)::(.*)::(ro|rw)/
      if (vol.matches()) {
        def readOnly=true
        if (vol[0][3] == "rw") { readOnly=false }
        def slaveVol = new MesosSlaveInfo.Volume(
          containerPath=vol[0][2],
          hostPath=vol[0][1],
          readOnly=readOnly
        )
        slaveVols.add(slaveVol)
      } else {
        println "[${DateTime.now()}][mesos.groovy]  Error processing vol: ${it} -- ${env[it]}"
      }
    }
    if (slaveVols.isEmpty()) {slaveVols = null}


    env.keySet().grep(~/JENKINS_MESOS_SLAVE_${i}_PARAM_[0-9]{1,3}/).sort().each {
      def param = env["${it}"].trim() =~ /(.*)::(.*)/
      if (param.matches()) {
        def slaveParam = new MesosSlaveInfo.Parameter(
          key=param[0][1],
          value=param[0][2]
        )
        slaveParams.add(slaveParam)
      } else {
        println "[${DateTime.now()}][mesos.groovy]  Error processing param: ${it} -- ${env[it]}"
      }
    }
    if (slaveParams.isEmpty()) {slaveParams = null}

    env.keySet().grep(~/JENKINS_MESOS_SLAVE_${i}_PORTS_[0-9]{1,3}/).sort().each {
      def port = env["${it}"].trim() =~ /(\d+)::(\d+)::(tcp|udp)/
      if (port.matches()) {
        def slavePort = new MesosSlaveInfo.PortMapping(
          containerPort=port[0][2].toInteger(),
          hostPort=port[0][1].toInteger(),
          protocol=port[0][3]
        )
        slavePorts.add(slavePort)
      } else {
        println "[${DateTime.now()}][mesos.groovy]  Error processing port: ${it} -- ${env[it]}"
      }
    }
    if (slavePorts.isEmpty()) {slavePorts = null}


    // The below is a workaround for a 'No enum constant' error if attempting to set networking
    // dynamically from a variable.
    String slaveNet
    if (env["JENKINS_MESOS_SLAVE_${i}_NETWORKING"] == 'HOST') {
      slaveNet='HOST'
    } else {
      slaveNet='BRIDGE'
    }


    def slaveContainerInfo = new MesosSlaveInfo.ContainerInfo(
      type='DOCKER',
      dockerImage = env["JENKINS_MESOS_SLAVE_${i}_DOCK_IMG"] ?: '',
      dockerPrivilegedMode = (env["JENKINS_MESOS_SLAVE_${i}_PRIV_MODE"] ?: 'false' ).toBoolean(),
      dockerForcePullImage = (env["JENKINS_MESOS_SLAVE_${i}_FORCE_PULL"] ?: 'false').toBoolean(),
      useCustomDockerCommandShell = (env["JENKINS_MESOS_SLAVE_${i}_CMD_SHELL"] ?: 'false').toBoolean(),
      customDockerCommandShell = env["JENKINS_MESOS_SLAVE_${i}_CMD_SHELL_COMMAND"] ?: '',
      volumes = slaveVols,
      parameters = slaveParams,
      networking = slaveNet,
      portMappings = slavePorts
    )

    def slaveInfo = new MesosSlaveInfo(
      labelString = env["JENKINS_MESOS_SLAVE_${i}_LABEL"] ?: "mesos-${i}",
      mode=null,
      slaveCpus = env["JENKINS_MESOS_SLAVE_${i}_CPU"] ?: '0.1',
      slaveMem = env["JENKINS_MESOS_SLAVE_${i}_MEM"] ?: '512',
      maxExecutors = env["JENKINS_MESOS_SLAVE_${i}_MAX_EXEC"] ?: '2',
      executorCpus = env["JENKINS_MESOS_SLAVE_${i}_EXEC_CPU"] ?: '0.1',
      executorMem = env["JENKINS_MESOS_SLAVE_${i}_EXEC_MEM"] ?: '128',
      remoteFSRoot = env["JENKINS_MESOS_SLAVE_${i}_RFS_ROOT"] ?: 'jenkins',
      idleTerminationMinutes = env["JENKINS_MESOS_SLAVE_${i}_IDLE_TERM"] ?: '3',
      slaveAttributes = env["JENKINS_MESOS_SLAVE_${i}_SLAVE_ATTRIB"] ?: '',
      jvmArgs = env["JENKINS_MESOS_SLAVE_${i}_JVM_ARGS"],           //default in class
      jnlpArgs = env["JENKINS_MESOS_SLAVE_${i}_JNLP_ARGS"] ?: '',
      externalContainerInfo = null,
      containerInfo = slaveContainerInfo,
      additionalURIs = slaveUris,
    )

    slaveContainers.add(slaveInfo)
  }

  def cloud = new MesosCloud(
    nativeLibraryPath = '/usr/lib/libmesos.so',
    master = env['JENKINS_MESOS_MASTER'] ?: '',
    description = env['JENKINS_MESOS_DESCRIPTION'] ?: '',
    frameworkName = env['JENKINS_MESOS_FRAMEWORK_NAME'] ?: 'Jenkins Scheduler',
    slavesUser = env['JENKINS_MESOS_SLAVES_USER'] ?: 'jenkins',
    principal = env['JENKINS_MESOS_PRINCIPAL'] ?: 'jenkins',
    secret = env['JENKINS_MESOS_SECRET'] ?: '',
    slaveInfos = slaveContainers,
    checkpoint = (env['JENKINS_MESOS_CHECKPOINT'] ?: 'false').toBoolean(),
    onDemandRegistration = (env['JENKINS_MESOS_ON_DEMAND'] ?: 'true').toBoolean(),
    jenkinsURL = env['JENKINS_MESOS_URL'] ?: ''
  )

  cloudList.add(cloud)
}


def setMesosCloud(cloud, env) {
  println "[${DateTime.now()}][mesos.groovy]  Mesos Cloud already defined. Updating Configuration."
  if (env['JENKINS_MESOS_MASTER']) {cloud.setMaster(env['JENKINS_MESOS_MASTER'])}
  if (env['JENKINS_MESOS_DESCRIPTION']) {cloud.setDescription(env['JENKINS_MESOS_DESCRIPTION'])}
  if (env['JENKINS_MESOS_FRAMEWORK_NAME']) {cloud.setFrameworkName(env['JENKINS_MESOS_FRAMEWORK_NAME'])}
  if (env['JENKINS_MESOS_SLAVES_USER']) {cloud.setSlavesUser(env['JENKINS_MESOS_SLAVES_USER'])}
  if (env['JENKINS_MESOS_PRINCIPAL']) {cloud.setPrincipal(env['JENKINS_MESOS_PRINCIPAL'])}
  if (env['JENKINS_MESOS_SECRET']) {cloud.setSecret(env['JENKINS_MESOS_SECRET'])}
  if (env['JENKINS_MESOS_ON_DEMAND']) {cloud.setOnDemandRegistration(env['JENKINS_MESOS_ON_DEMAND'].toBoolean())}
  if (env['JENKINS_MESOS_URL']) {cloud.setJenkinsURL(env['JENKINS_MESOS_URL'])}
}


def instance = Jenkins.getInstance()
def env = System.getenv()

if ((instance.pluginManager.activePlugins.find { it.shortName == 'mesos' } != null ) &&
    (env['JENKINS_MESOS_AUTOCONF'] == 'enabled' )) {

  println "[${DateTime.now()}][mesos.groovy] Mesos plugin autoconfiguration enabled. Disabling Master executors."
  instance.setNumExecutors(0)

  def cloudList = instance.clouds
  def mesosClouds = []
  if (cloudList.each {if (it instanceof org.jenkinsci.plugins.mesos.MesosCloud) {mesosClouds.add(it); return true }}) {
    mesosClouds.each {setMesosCloud(it, env)}
  } else {
    addMesosCloud(cloudList, env)
  }
}
