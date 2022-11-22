# Camunda Connector Secret Provider for GCP Security Manager

Secret Provider that delegates to the GCP Security Manager.

Requires the following properties being set,e.g. via application.properties:

* `zeebe.client.cloud.cluster-id`: Cluster ID of Camunda 8 SaaS cluster
* `camunda.secrets.project.id`: GCP project ID to use
* `camunda.secrets.prefix`: Prefix, that is used together with clusterId to determine the right entry in the secret store

or as environment variables:

```bash
ZEEBE_CLIENT_CLOUD_CLUSTER-ID
CAMUNDA_SECRETS_PROJECT_ID
CAMUNDA_SECRETS_PREFIX
```
