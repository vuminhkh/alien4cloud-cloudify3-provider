#!/bin/bash

VOLUME_HOME=$(ctx node properties storage_path)
PORT=$(ctx node properties db_port)
DB_NAME=$(ctx node properties db_name)
DB_USER=$(ctx node properties db_user)
DB_PASSWORD=$(ctx node properties db_password)
BIND_ADDRESS=$(ctx node properties bind_address)

echo "------------------------ ENV ------------------------"
echo "ENV VAR USED VOLUME_HOME : $VOLUME_HOME"
echo "ENV VAR USED PORT        : $PORT"
echo "ENV VAR USED DB_NAME     : $DB_NAME"
echo "ENV VAR USED DB_USER     : $DB_USER"
echo "ENV VAR USED DB_PASSWORD : $DB_PASSWORD"
echo "---------------------------- ------------------------"

CURRENT_PATH=`dirname "$0"`

function StartMySQL {
  ctx logger info "Starting MYSQL..."
  sudo /etc/init.d/mysql stop
  sudo /usr/bin/mysqld_safe > /dev/null 2>&1 &
  RET=1
  while [[ RET -ne 0 ]]; do
    ctx logger info "=> Waiting for confirmation of MySQL service startup"
    sleep 5
    sudo mysql -uroot -e "status" > /dev/null 2>&1
    RET=$?
  done
}

function AllowFileSystemToMySQL {
  MYSQL_DATA_DIR=$VOLUME_HOME/data
  MYSQL_LOG=$VOLUME_HOME/logs

  ctx logger info "Setting data directory to $MYSQL_DATA_DIR an logs to $MYSQL_LOG ..."
  if sudo test ! -d $MYSQL_DATA_DIR; then
    ctx logger info "Creating DATA dir > $MYSQL_DATA_DIR ..."
    sudo mkdir -p $MYSQL_DATA_DIR
    # mysql as owner and group owner
    sudo chown -R mysql:mysql $MYSQL_DATA_DIR
  fi
  if sudo test ! -d $MYSQL_LOG; then
    ctx logger info "Creating LOG dir > $MYSQL_LOG ..."
    sudo mkdir -p $MYSQL_LOG
    # mysql as owner and group owner
    sudo chown -R mysql:mysql $MYSQL_LOG
  fi

  # edit app mysql permission in : /etc/apparmor.d/usr.sbin.mysqld
  COUNT_LINE=`sudo cat /etc/apparmor.d/usr.sbin.mysqld | wc -l`
  sudo sed -i "$(($COUNT_LINE)) i $MYSQL_DATA_DIR/ r," /etc/apparmor.d/usr.sbin.mysqld
  sudo sed -i "$(($COUNT_LINE)) i $MYSQL_DATA_DIR/** rwk," /etc/apparmor.d/usr.sbin.mysqld
  sudo sed -i "$(($COUNT_LINE)) i $MYSQL_LOG/ r," /etc/apparmor.d/usr.sbin.mysqld
  sudo sed -i "$(($COUNT_LINE)) i $MYSQL_LOG/** rwk," /etc/apparmor.d/usr.sbin.mysqld

  # reload app permission manager service
  sudo service apparmor reload
}

function UpdateMySQLConf {
  ctx logger info "Updating MySQL conf files [DATA, LOGS]..."
  sudo sed -i "s:/var/lib/mysql:$MYSQL_DATA_DIR:g" /etc/mysql/my.cnf
  sudo sed -i "s:/var/log/mysql/error.log:$MYSQL_LOG/error.log:g" /etc/mysql/my.cnf
  sudo sed -i "s:3306:$PORT:g" /etc/mysql/my.cnf

  if sudo test ! -f /usr/share/mysql/my-default.cnf; then
    sudo cp /etc/mysql/my.cnf /usr/share/mysql/my-default.cnf
  fi
  if sudo test ! -f /etc/mysql/conf.d/mysqld_charset.cnf; then
    sudo cp $CURRENT_PATH/mysqld_charset.cnf /etc/mysql/conf.d/mysqld_charset.cnf
  fi

  if [ "$BIND_ADDRESS" == "true" ]; then
    sudo sed -i "s/bind-address.*/bind-address = 0.0.0.0/" /etc/mysql/my.cnf
  fi
}

function InitMySQLDb {
  # create database DB_NAME
  if [ "$DB_NAME" ]; then
    ctx logger info "INIT DATABASE $DB_NAME"
    sudo mysql -u root -e "CREATE DATABASE $DB_NAME";
  fi

  # create user and give rights
  if [ "$DB_USER" ]; then
    ctx logger info "CREATE USER $DB_USER WITH PASSWORD $DB_PASSWORD AND GRAND RIGHTS ON $DB_NAME"
    sudo mysql -uroot -e "CREATE USER '${DB_USER}'@'%' IDENTIFIED BY '$DB_PASSWORD'"
    sudo mysql -uroot -e "GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'%' WITH GRANT OPTION"
    sudo mysql -uroot -e "FLUSH PRIVILEGES"
  fi
}

# Create a new database path to the attched volume
if sudo test ! -d $VOLUME_HOME/data; then
  ctx logger info "=> An empty or uninitialized MySQL volume is detected in $VOLUME_HOME/data"
  AllowFileSystemToMySQL
  UpdateMySQLConf
  ctx logger info "=> Init new database path to $MYSQL_DATA_DIR"
  sudo mysql_install_db --basedir=/usr --datadir=$MYSQL_DATA_DIR
  ctx logger info "=> MySQL database initialized !"
else
  ctx logger info "=> Using an existing volume of MySQL"
  AllowFileSystemToMySQL
  UpdateMySQLConf
fi

# Finally start MySQL with new configuration
StartMySQL
InitMySQLDb
