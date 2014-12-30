#!/bin/bash

ctx logger info "Debian based MYSQL install 5..."
LOCK="/tmp/lockaptget"

while true; do
  if mkdir "${LOCK}" &>/dev/null; then
    ctx logger info "MySQL take the lock"
    break;
  fi
  ctx logger info "Waiting the end of one of our recipes..."
  sleep 0.5
done

while sudo fuser /var/lib/dpkg/lock >/dev/null 2>&1 ; do
  ctx logger info "Waiting for other software managers to finish..."
  sleep 0.5
done
sudo rm -f /var/lib/dpkg/lock

sudo apt-get update || echo "Failed on: sudo apt-get update"
sudo DEBIAN_FRONTEND=noninteractive apt-get -y install mysql-server-5.5 pwgen || exit ${1}
rm -rf "${LOCK}"

sudo /etc/init.d/mysql stop
sudo rm -rf /var/lib/apt/lists/*
sudo rm -rf /var/lib/mysql/*
ctx logger info "MySQL Installation complete."
