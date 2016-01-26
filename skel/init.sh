#!/bin/bash

source /opt/scripts/container_functions.lib.sh

init_vars() {

  if [[ $ENVIRONMENT_INIT && -f $ENVIRONMENT_INIT ]]; then
    source "$ENVIRONMENT_INIT"
  fi 

  if [[ ! $PARENT_HOST && $HOST ]]; then
    export PARENT_HOST="$HOST"
  fi

  export APP_NAME=${APP_NAME:-jenkins}
  export ENVIRONMENT=${ENVIRONMENT:-local}
  export PARENT_HOST=${PARENT_HOST:-unknown}

  export LIBPROCESS_PORT=${LIBPROCESS_PORT:-9000}

  export JENKINS_LOG_FILE_PATTERN=${JENKINS_LOG_FILE_PATTERN:-/var/log/jenkins/jenkins.log}
  export JENKINS_HTTP_LISTEN_ADDRESS=${JENKINS_HTTP_LISTEN_ADDRESS:-0.0.0.0}
  export JENKINS_HTTP_PORT=${JENKINS_HTTP_PORT:-8080}
  export JENKINS_JNLP_PORT=${JENKINS_JNLP_PORT:-8090}

  # if consul template is to be used, configure rsyslog
  export SERVICE_CONSUL_TEMPLATE=${SERVICE_CONSUL_TEMPLATE:-disabled}
  if [[ "$SERVICE_CONSUL_TEMPLATE" == "enabled" ]]; then
    export SERVICE_RSYSLOG=${SERVICE_RSYSLOG:-enabled}
  fi

  export SERVICE_LOGSTASH_FORWARDER_CONF=${SERVICE_LOGSTASH_FORWARDER_CONF:-/opt/logstash-forwarder/jenkins.conf}
  export SERVICE_REDPILL_MONITOR=${SERVICE_REDPILL_MONITOR:-jenkins}


  case "${ENVIRONMENT,,}" in
    prod|production|dev|development)
      export JENKINS_LOG_STDOUT_THRESHOLD=${JENKINS_LOG_STDOUT_THRESHOLD:-WARNING}
      export JENKINS_LOG_FILE_THRESHOLD=${JENKINS_LOG_FILE_THRESHOLD:-WARNING}
      export SERVICE_LOGSTASH_FORWARDER=${SERVICE_LOGSTASH_FORWARDER:-enabled}
      export SERVICE_REDPILL=${SERVICE_REDPILL:-enabled}
    ;;
    debug)
      export JENKINS_LOG_STDOUT_THRESHOLD=${JENKINS_LOG_STDOUT_THRESHOLD:-FINEST}
      export JENKINS_LOG_FILE_THRESHOLD=${JENKINS_LOG_FILE_THRESHOLD:-FINEST}
      export SERVICE_LOGSTASH_FORWARDER=${SERVICE_LOGSTASH_FORWARDER:-disabled}
      export SERVICE_REDPILL=${SERVICE_REDPILL:-disabled}
      if [[ "$SERVICE_CONSUL_TEMPLATE" == "enabled" ]]; then
        export CONSUL_TEMPLATE_LOG_LEVEL=${CONSUL_TEMPLATE_LOG_LEVEL:-debug}
      fi
    ;;
    local|*)
      export JAVA_OPTS=${JAVA_OPTS:-"-Xmx256m"}
      export JENKINS_LOG_STDOUT_THRESHOLD=${JENKINS_LOG_STDOUT_THRESHOLD:-WARNING}
      export JENKINS_LOG_FILE_THRESHOLD=${JENKINS_LOG_FILE_THRESHOLD:-WARNING}
      export SERVICE_LOGSTASH_FORWARDER=${SERVICE_LOGSTASH_FORWARDER:-disabled}
      export SERVICE_REDPILL=${SERVICE_REDPILL:-enabled}
    ;;
  esac

}

config_jenkins() {
  sed -i -e "s|^java\.util\.logging\.FileHandler\.pattern=.*|java.util.logging.FileHandler.pattern=$JENKINS_LOG_FILE_PATTERN|g"        \
         -e "s|^java\.util\.logging\.FileHandler\.level=.*|java.util.logging.FileHandler.level=$JENKINS_LOG_FILE_THRESHOLD|g"          \
         -e "s|^java\.util\.logging\.ConsoleHandler\.level=.*|java.util.logging.ConsoleHandler.level=$JENKINS_LOG_STDOUT_THRESHOLD|g"  \
            "$JENKINS_HOME/logging.properties"

  local jvm_opts=()
  local cmd_flags=()
  local jnks_cmd=""

  jvm_opts=( "-Djava.awt.headless=true"
             "-Djava.util.logging.config.file=$JENKINS_HOME/logging.properties" )

  for j_opt in $JAVA_OPTS; do
    jvm_opts+=( ${j_opt} )
  done

  for i in $(compgen -A variable | awk '/^JENKINS_/ && !/^JENKINS_ARGS/ && !/^JENKINS_HOME/ && !/^JENKINS_JNLP/ && !/^JENKINS_LOG_FILE_/ && !/^JENKINS_LOG_STDOUT_/ && !/^JENKINS_MESOS/'); do
    var_name="--$(echo "$i" | awk '{print tolower(substr($1,9))}' | sed -r 's/(_)(.)/\U\2/g')"
    cmd_flags+=( "$var_name=${!i}" )
  done

  for j_arg in $JENKINS_ARGS; do
    cmd_flags+=( ${j_arg} )
  done

  jnks_cmd="/usr/bin/java ${jvm_opts[*]} -jar /usr/share/jenkins/jenkins.war ${cmd_flags[*]}"
  export SERVICE_JENKINS_CMD=${SERVICE_JENKINS_CMD:-"$(__escape_svsr_txt "$jnks_cmd")"}
}

main() {

  init_vars

  echo "[$(date)][App-Name] $APP_NAME"
  echo "[$(date)][Environment] $environment"

  __config_service_consul_template
  __config_service_logstash_forwarder
  __config_service_redpill
  __config_service_rsyslog

  config_jenkins

  echo "[$(date)][Jenkins][Start-Command] $SERVICE_JENKINS_CMD"

  exec supervisord -n -c /etc/supervisor/supervisord.conf

}

main "$@"
