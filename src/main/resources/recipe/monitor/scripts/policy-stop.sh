#! /bin/bash 
read PID < /root/pid_file
sudo kill -9 $PID
crontab -r


