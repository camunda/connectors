# system integration e2e test for ServiceNow

CPT is used with a custom bootstrapped Connectors runtime.
To run the SN system integration test(s) against a live SN instance, you need to provide the following configuration:

- three env vars pointing to the live SN instance (that can eventually be part of the above Spring config):
  - `SN_INSTANCE` representing the host name only, e.g. `dev123`
  - `SN_USER` for the SN user name
  - `SN_PASSWORD` for the SN user password

- an env var enabling the SN system integration test that in turn correlates with the `@SystemIntegrationTest(with = ExternalSystem.ServiceNow)` annotation:
  - `export SYSTEM_INTEGRATION_TEST_SERVICENOW=any-non-empty-value`
  - `@SystemIntegrationTest(with = ExternalSystem.ServiceNow)` for the test class