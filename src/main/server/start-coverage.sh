#!/bin/bash

if [ -z "$RED5_HOME" ]; then 
  export RED5_HOME=.; 
fi

export JAVA_OPTS="-javaagent:org.jacoco.agent-0.8.6-runtime.jar=includes=*,output=file,destfile=/home/ubuntu/jacoco.exec $JAVA_OPTS"

# Start Red5
exec $RED5_HOME/start.sh
