#! /bin/bash
DPLID=$(ctx deployment id)
ctx logger info "deployment_id = ${DPLID}"
read PID < /tmp/pid_file_${DPLID}
sudo kill -9 $PID
crontab -r
