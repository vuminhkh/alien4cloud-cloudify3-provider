#!/bin/sh

currHostName=`hostname`

currFilename=$(basename "$0")

echo "${currHostName}:${currFilename} Java url ${JAVA_URL}"

echo "${currHostName}:${currFilename} Java home ${JAVA_HOME}"

if [ -f /usr/bin/wget ]; then
	DOWNLOADER="wget"
elif [ -f /usr/bin/curl ]; then
	DOWNLOADER="curl"
fi

echo "${currHostName}:${currFilename} :DOWNLOADER ${DOWNLOADER}"

# args:
# $1 the error code of the last command (should be explicitly passed)
# $2 the message to print in case of an error
#
# an error message is printed and the script exists with the provided error code
error_exit () {
	echo "${currHostName}:${currFilename} $2 : error code: $1"
	exit $1
}

# args:
# $1 download description.
# $2 download link.
# $3 output file.
download () {
	echo "${currHostName}:${currFilename} Downloading $1 from $2 ..."
	if [ "$DOWNLOADER" = "wget" ];then
		Q_FLAG="--no-check-certificate"
		O_FLAG="-O"
		LINK_FLAG=""
	elif [ "$DOWNLOADER" = "curl" ];then
		Q_FLAG=""
		O_FLAG="-o"
		LINK_FLAG="-O"
	fi
	echo "${currHostName}:${currFilename} $DOWNLOADER $4 $Q_FLAG $O_FLAG $3 $LINK_FLAG $2"
	sudo $DOWNLOADER --header "Cookie: oraclelicense=accept-securebackup-cookie" $Q_FLAG $O_FLAG $3 $LINK_FLAG $2 || error_exit $? "Failed downloading $1"
}

# create java home if not exist
if [ ! -d "$JAVA_HOME" ]; then
    sudo mkdir -p $JAVA_HOME
fi

echo "${currHostName}:${currFilename} Downloading ${JAVA_URL} to ${destJavaArchive} ..."
download "JDK" $JAVA_URL $JAVA_HOME/java_archive.tar.gz

# Install java
sudo tar xzvf $JAVA_HOME/java_archive.tar.gz --strip 1 -C $JAVA_HOME
sudo rm $JAVA_HOME/java_archive.tar.gz

# Export Java home for all users
sudo echo "export JAVA_HOME=$JAVA_HOME" >> /etc/profile