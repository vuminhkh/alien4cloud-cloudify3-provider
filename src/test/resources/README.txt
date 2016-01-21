1. Authenticate on Openstack

 http://129.185.67.11:5000/v2.0/tokens
{
  "auth": {
    "tenantName": "Alien4Cloud",
    "passwordCredentials": {
      "username": "user",
      "password": "password"
    }
  }
}

2. Check instance state:

http://129.185.67.11:8774/v2/a04007f8e8e34867973059cb4a27a66d/servers/9d5ef146-17f5-43fd-941d-3b4dc4e5c968

HEADER:

X-Auth-Token = "Token id retrieved from above"

3. Bootstrap 3.3. GA :

  - Clone the git repository https://fastconnect.org/gitlab/cloudify/cloudify-cli-docker-image.git

  - File /opt/cfy/cloudify-manager-blueprints-commercial/components/restservice/config/cloudify-rest.conf
    find the key "insecure_endpoints_disabled" and replace the whole line with the folowing one:
    insecure_endpoints_disabled: {{ ctx.node.properties.insecure_endpoints_disabled }}

  - File /opt/cfy/env/lib/python2.7/site-packages/cloudify_rest_client/plugins.py:

    replace with src/test/resources/plugins.py
    rm /opt/cfy/env/lib/python2.7/site-packages/cloudify_rest_client/plugins.pyc

  - File /opt/cfy/cloudify-manager-blueprints-commercial/openstack-manager-blueprint.yaml
    File /opt/cfy/cloudify-manager-blueprints-commercial/aws-ec2-manager-blueprint.yaml
      * find agents_security_group definition
      * replace everithing with:
          agents_security_group
            type: cloudify.openstack.nodes.SecurityGroup
            properties:
              resource_id: { get_input: agents_security_group_name }
              use_external_resource: true
              openstack_config: *openstack_configuration
      * Do the same with management_security_group:
          management_security_group:
            type: cloudify.openstack.nodes.SecurityGroup
            properties:
              resource_id: { get_input: manager_security_group_name }
              use_external_resource: true
              openstack_config: *openstack_configuration

  - in the inputs file:
    Change the blueprint inputs so that It uses existing security group openbar instead of creating new ones
    set manager_security_group_name and agents_security_group_name to "openbar"

  - Bootstrap the manager.

On the bootstrapped manager:
  - Install python devel and gcc
    sudo yum -y install gcc
    sudo yum -y install python-devel
