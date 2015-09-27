import hudson.model.*
import jenkins.model.*
import org.joda.time.DateTime

Thread.start {
  def env = System.getenv()
  if (env['JENKINS_JNLP_PORT']?.isNumber()) {
    sleep 10000
    Jenkins.instance.setSlaveAgentPort(env['JENKINS_JNLP_PORT'].toInteger())
    println "[${DateTime.now()}][jnlp.groovy]  Jenkins JNLP port set to ${env['JENKINS_JNLP_PORT']}"
  }
}
