#!/bin/sh

currHostName=`hostname`
currFilename=$(basename "$0")

echo "${currHostName}:${currFilename} Deploying war for Tomcat in ${TOMCAT_HOME}..."

echo "${currHostName}:${currFilename} War's context path ${CONTEXT_PATH}"

# The war_file is the artifact path defined in the deployment artifacts of the SOURCE which is injected here automatically
# The real solution is to implement the get_artifact function
echo "${currHostName}:${currFilename} War file path is at ${war_file}"

tomcatConfFolder=$TOMCAT_HOME/conf
tomcatContextPathFolder=$tomcatConfFolder/Catalina/localhost
tomcatContextFile=$tomcatContextPathFolder/$CONTEXT_PATH.xml

mkdir -p $tomcatContextPathFolder

# Write the context configuration
rm -f $tomcatContextFile
echo "<Context docBase=\"${war_file}\" path=\"${CONTEXT_PATH}\" />" > $tomcatContextFile
echo "${currHostName}:${currFilename} Sucessfully installed war on Tomcat in ${TOMCAT_HOME}"