# Camunda Connectors Secret Provider

For AWS SaaS Clusters the AWS Secret Manager is used. For GCP SaaS Clusters the GCP Secret Manager is used.

## Configuration

Secret Provider that delegates to the GCP Security Manager.

Requires the following properties being set,e.g. via application.properties:

* `camunda.client.cloud.clusterId`: Cluster ID of Camunda SaaS cluster
* `camunda.saas.secrets.projectId`: GCP project ID to use

Optionally the following property can be set:
* `camunda.saas.secrets.prefix`: If not set defaults to `connector-secrets`

or as environment variables all these variables need to be set:

```bash
CAMUNDA_CLUSTER_ID
SECRETS_PROJECT_ID
SECRETS_PREFIX
```

## Authentication

### GCP

The [Application Default Credentials](https://cloud.google.com/docs/authentication/application-default-credentials) are used. Make sure to configure one of the sources.

### AWS

The [Default Credential Provider Chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html) is used. Make sure to configure one of the sources.
