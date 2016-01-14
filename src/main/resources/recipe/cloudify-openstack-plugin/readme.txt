# before build :

git clone -b master http://fastconnect.org/gitlab/cloudify-drivers/cloudify-openstack-plugin.git
rm -rf cloudify-openstack-plugin/.git
zip -r cloudify-openstack-plugin.zip cloudify-openstack-plugin/*


# Import URLs in orchestrator config :

- http://www.getcloudify.org/spec/cloudify/3.2/types.yaml
- openstack-plugin.yaml


sudo yum install gcc
sudo yum install python-devel