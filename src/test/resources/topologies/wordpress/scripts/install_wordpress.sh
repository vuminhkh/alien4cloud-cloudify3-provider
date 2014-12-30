#!/bin/sh
WEBFILE_URL=$(ctx node properties zip_url)
ctx logger info "Install wordpress from directory ${PWD}, download url is $WEBFILE_URL"
if ! type "unzip" > /dev/null; then
  ctx logger info "Install unzip..."
  sudo apt-get update || exit ${1}
  while sudo fuser /var/lib/dpkg/lock >/dev/null 2>&1 ; do
    ctx logger info "Waiting for other software managers to finish..."
    sleep 2
  done
  sudo apt-get install unzip || exit ${1}
fi

nameZip=${WEBFILE_URL##*/}
ctx logger info "Dowload last build of Wordpress from $WEBFILE_URL to /tmp/$nameZip"
wget $WEBFILE_URL -O /tmp/$nameZip

ctx logger info "Unzip wordpress from /tmp/$nameZip to /opt/wordpress"
sudo unzip -o /tmp/$nameZip -d /opt/wordpress