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

  - File /opt/cfy/cloudify-manager-blueprints-commercial/components/restservice/config/cloudify-rest.conf

    insecure_endpoints_disabled: {{ ctx.node.properties.insecure_endpoints_disabled }}

  - File /opt/cfy/env/lib/python2.7/site-packages/cloudify_rest_client/plugins.py:

    replace with src/test/resources/plugins.py
    rm /opt/cfy/env/lib/python2.7/site-packages/cloudify_rest_client/plugins.pyc

  - File /opt/cfy/cloudify-manager-blueprints-commercial/openstack-manager-blueprint.yaml
    File /opt/cfy/cloudify-manager-blueprints-commercial/aws-ec2-manager-blueprint.yaml
    Change the blueprint so that It uses existing security group openbar instead of creating new ones
    use_external_resource: true