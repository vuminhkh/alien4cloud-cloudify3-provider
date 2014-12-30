#!/bin/sh
WEBFILE_URL=$(ctx node properties zip_url)

if ! type "unzip" > /dev/null; then
  echo "Install unzip..."
  sudo apt-get update || exit ${1}
  while sudo fuser /var/lib/dpkg/lock >/dev/null 2>&1 ; do
    echo "Waiting for other software managers to finish..."
    sleep 2
  done
  sudo apt-get install unzip || exit ${1}
fi

echo "Dowload and unzip the last build of Wordpress in $DOC_ROOT/$CONTEXT_PATH..."
nameZip=${WEBFILE_URL##*/}
eval "wget $WEBFILE_URL -O ~/$nameZip"

eval "unzip -o ~/$nameZip -d ~"