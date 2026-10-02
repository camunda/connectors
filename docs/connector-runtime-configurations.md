# Connector Runtime Configurations

This page lists configuration properties you have to include to successfully connect a local Connector runtime to
the mandatory external services (Zeebe, Operate, etc.).

Overwrite the content of the `application.properties` file with the specific configuration.

> **Note:** The values below are examples only. Replace them with your own cluster-specific values, and never commit
> real client IDs or client secrets to this repository.

## Managed-code deployment reconciliation

Managed-code deployment reconciliation is an optional workload and is disabled by default. Enabling
it does not add a control-plane transport or a production cloud provider: those are supplied as
`ManagedScriptControlPlane` and `ManagedCodeDeploymentProvider` beans. For deterministic local
validation, the built-in fake provider can be selected explicitly. Startup fails when the feature
is enabled without both required beans, so a missing reconciliation dependency cannot become a
silent no-op.

```properties
camunda.connector.managed-code.enabled=true
camunda.connector.managed-code.provider=fake
camunda.connector.managed-code.worker=managed-code-runtime
camunda.connector.managed-code.interval=5s
camunda.connector.managed-code.initial-delay=0s
camunda.connector.managed-code.lease-duration=2m
camunda.connector.managed-code.batch-size=20
camunda.connector.managed-code.concurrency=4
camunda.connector.managed-code.queue-capacity=64
camunda.connector.managed-code.shutdown-timeout=30s
```

The reconciler isolates physical tenants, prevents overlapping work for the same physical tenant,
and leaves normal connector workers unchanged when disabled. See
[ADR-0008](adr/ADR-0008-managed-code-deployment-service.md) for the architectural boundary.

For trusted local POC validation only, fake-provider deployments can be invoked in bounded Node.js
or Python subprocesses:

```properties
camunda.connector.managed-code.local-execution-enabled=true
camunda.connector.managed-code.execution-timeout=30s
camunda.connector.managed-code.invocation-concurrency=4
```

Node.js 20 or newer and Python 3.10 or newer are discovered from `PATH`. The script must export or
define `execute(variables, context)` and return a JSON object; its properties become job completion
variables. This mode executes scripts with the Connector Runtime operating-system identity. It
limits time and I/O but is **not a sandbox** and must not be enabled for untrusted or production
workloads.

## Connect to INT SaaS from a local connector runtime

```properties
# API credentials
zeebe.client.cloud.region=bru-2
zeebe.client.cloud.clusterId=<your-cluster-id>
zeebe.client.cloud.clientId=<your-client-id>
zeebe.client.cloud.clientSecret=<your-client-secret>

# zeebe config
zeebe.client.cloud.base-url=zeebe.ultrawombat.com
zeebe.client.cloud.auth-url=https://login.cloud.ultrawombat.com/oauth/token

# operate config
operate.client.enabled=true
camunda.operate.client.url=https://bru-2.operate.ultrawombat.com/<your-cluster-id>
camunda.operate.client.authUrl=https://login.cloud.ultrawombat.com/oauth/token
camunda.operate.client.baseUrl=operate.ultrawombat.com

# inbound config
camunda.connector.webhook.enabled=true
camunda.connector.polling.enabled=true
camunda.connector.polling.interval=5000

# Secret config
camunda.connector.secretprovider.console.enabled=true
camunda.connector.secretprovider.console.endpoint=https://cluster-api.cloud.ultrawombat.com/secrets
camunda.connector.secretprovider.console.audience=secrets.ultrawombat.com
```

## Connect to DEV SaaS from a local connector runtime

```properties
# API credentials
zeebe.client.cloud.region=lpp-1
zeebe.client.cloud.clusterId=<your-cluster-id>
zeebe.client.cloud.clientId=<your-client-id>
zeebe.client.cloud.clientSecret=<your-client-secret>

# zeebe config
zeebe.client.cloud.base-url=<your-cluster-id>.lpp-1.zeebe.dev.ultrawombat.com:443
zeebe.client.cloud.auth-url=https://login.cloud.dev.ultrawombat.com/oauth/token

# operate config
operate.client.enabled=true
camunda.operate.client.url=https://lpp-1.operate.dev.ultrawombat.com/<your-cluster-id>
camunda.operate.client.authUrl=https://login.cloud.dev.ultrawombat.com/oauth/token
camunda.operate.client.baseUrl=operate.dev.ultrawombat.com

# inbound config
camunda.connector.webhook.enabled=true
camunda.connector.polling.enabled=true
camunda.connector.polling.interval=5000

# Secret config
camunda.connector.secretprovider.console.enabled=true
camunda.connector.secretprovider.console.endpoint=https://cluster-api.cloud.dev.ultrawombat.com/secrets
camunda.connector.secretprovider.console.audience=secrets.dev.ultrawombat.com
```

## Connect to PROD SaaS from a local connector runtime

```properties
# API credentials
zeebe.client.cloud.region=bru-2
zeebe.client.cloud.clusterId=<your-cluster-id>
zeebe.client.cloud.clientId=<your-client-id>
zeebe.client.cloud.clientSecret=<your-client-secret>

# operate config
operate.client.enabled=true

# inbound config
camunda.connector.webhook.enabled=true
camunda.connector.polling.enabled=true
camunda.connector.polling.interval=5000
```
