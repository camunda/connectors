# system integration e2e test for ServiceNow

to run the SN system integration test(s) against a live SN instance, you need to provide the following configuration:

- Camunda Client Spring config, example:
  ```yaml
  camunda:
  client:
    mode: saas
    auth:
      client-id: ...
      client-secret: ...
      token-url: https://login.cloud.dev.ultrawombat.com/oauth/token
      audience: zeebe.dev.ultrawombat.com
    cloud:
      cluster-id: ...
      region: ...

    grpc-address: https:// ... . ... .zeebe.dev.ultrawombat.com
    rest-address:  https:// ... .zeebe.dev.ultrawombat.com/...
   ```  

- three env vars pointing to the live SN instance (that can eventually be part of the above Spring config):
  - `SN_INSTANCE` representing the host name only, e.g. `dev123`
  - `SN_USER` for the SN user name
  - `SN_PASSWORD` for the SN user password

- an env var enabling the SN system integration test that in turn correlates with the `@SystemIntegrationTest(with = ExternalSystem.ServiceNow)` annotation:
  - `export SYSTEM_INTEGRATION_TEST_ServiceNow=any-non-empty-value`
  - `@SystemIntegrationTest(with = ExternalSystem.ServiceNow)` for the test class