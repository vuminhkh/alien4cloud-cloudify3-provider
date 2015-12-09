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

3. Migration 3.3:

  - types of cloudify.datatypes.AgentConfig : env extra
