#!/bin/bash

CONTEXT_PATH=$(ctx source node properties context_path)
DOC_ROOT=$(ctx target node properties document_root)

if [ "$CONTEXT_PATH" == "/" ]; then
  CONTEXT_PATH=""
fi

if [ ! -d $DOC_ROOT/$CONTEXT_PATH ]; then
  eval "sudo mkdir -p $DOC_ROOT/$CONTEXT_PATH"
fi

eval "sudo rm -rf $DOC_ROOT/$CONTEXT_PATH/*"
eval "sudo mv -f ~/wordpress/* $DOC_ROOT/$CONTEXT_PATH"
eval "sudo chown -R www-data:www-data $DOC_ROOT/$CONTEXT_PATH"
eval "sudo chmod 777 -R $DOC_ROOT/$CONTEXT_PATH"

echo "End of Wordpress install, restart apache2 to charge all modules"
sudo /etc/init.d/apache2 restart