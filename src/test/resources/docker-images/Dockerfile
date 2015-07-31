FROM phusion/baseimage
RUN \
  apt-get update && \
  apt-get install -y python python-dev python-pip python-virtualenv && \
  rm -rf /var/lib/apt/lists/*
RUN apt-get update && apt-get install -y git
# Change this if you want to install another version of cloudify
ENV CFY_BRANCH tags/3.2
ENV CFY_SCRIPT_BRANCH tags/1.2
RUN pip install https://github.com/cloudify-cosmo/cloudify-dsl-parser/archive/$CFY_BRANCH.zip
RUN pip install https://github.com/cloudify-cosmo/cloudify-rest-client/archive/$CFY_BRANCH.zip
RUN pip install https://github.com/cloudify-cosmo/cloudify-plugins-common/archive/$CFY_BRANCH.zip
RUN pip install https://github.com/cloudify-cosmo/cloudify-script-plugin/archive/$CFY_SCRIPT_BRANCH.zip
RUN pip install https://github.com/cloudify-cosmo/cloudify-cli/archive/$CFY_BRANCH.zip
RUN mkdir /cfybootstrap
WORKDIR /cfybootstrap
RUN cfy init
ADD confs /cfybootstrap
RUN git clone https://github.com/cloudify-cosmo/cloudify-manager-blueprints.git
WORKDIR /cfybootstrap/cloudify-manager-blueprints
RUN git fetch
RUN git checkout $CFY_BRANCH
RUN cfy local create-requirements -o requirements-os.txt -p /cfybootstrap/cloudify-manager-blueprints/openstack/openstack-manager-blueprint.yaml
RUN pip install -r requirements-os.txt
RUN rm requirements-os.txt
RUN cfy local create-requirements -o requirements-aws.txt -p /cfybootstrap/cloudify-manager-blueprints/aws-ec2/aws-ec2-manager-blueprint.yaml
RUN pip install -r requirements-aws.txt
RUN rm requirements-aws.txt
# Define default command
WORKDIR /cfybootstrap
