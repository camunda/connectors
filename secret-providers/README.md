# Camunda Connectors Secret Provider

## Configuration

Pass your configuration as environment variables, by setting:

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
