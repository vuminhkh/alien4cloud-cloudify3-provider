#! /bin/bash
#exit
pip install influxdb
if [ $? -gt 0 ]; then
  ctx logger info "Aborting ... "
  exit
fi


ctx logger info "Retrieving nodes_to_monitor and deployment_id"

NTM="$(ctx node properties nodes_to_monitor)"
ctx logger info "nodes_to_monitor = ${NTM}"
NTM=$(echo ${NTM} | sed "s/u'/'/g")
DPLID=$(ctx deployment id)
ctx logger info "deployment_id = ${DPLID}"

LOC=$(ctx download-resource monitor/scripts/policy.py)

CRON_FILE=/tmp/policycron_${DPLID}

rm $CRON_FILE

COMMAND="/root/cloudify.${DPLID}/env/bin/python ${LOC} \"${NTM}\" ${DPLID} > /tmp/logfile_cron_${DPLID}"
echo "*/1 * * * * $COMMAND" >> $CRON_FILE
crontab $CRON_FILE

ctx logger info "qui:`whoami` - ou:`hostname` - crontab: `crontab -l` - ls: `ls /tmp`"
