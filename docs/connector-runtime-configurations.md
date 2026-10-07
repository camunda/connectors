# Connector Runtime Configurations

This page lists configuration properties you have to include to successfully connect a local Connector runtime to
the mandatory external services (Zeebe, Operate, etc.).

Overwrite the content of the `application.properties` file with the specific configuration.

> **Note:** The values below are examples only. Replace them with your own cluster-specific values, and never commit
> real client IDs or client secrets to this repository.

## Managed scripts

The managed-script worker is optional and disabled by default. When enabled, it activates
`io.camunda:managed-script:1` jobs, fetches the script linked to the task, provisions it through the
configured provider the first time its content is used, and invokes it. Provisioning is lazy: the
first job of a new script waits for it, later jobs reuse the deployment. See
[ADR-0008](adr/ADR-0008-managed-script-lazy-provisioning.md).

```properties
camunda.connector.managed-code.enabled=true
camunda.connector.managed-code.provider=fake
camunda.connector.managed-code.worker-name=managed-script-worker
camunda.connector.managed-code.execution-timeout=30s
camunda.connector.managed-code.provisioning-timeout=2m
camunda.connector.managed-code.provisioning-retry-backoff=10s
camunda.connector.managed-code.invocation-concurrency=4
camunda.connector.managed-code.provisioning-concurrency=2
camunda.connector.managed-code.fake.provisioning-delay=2s
```

The job timeout is `provisioning-timeout + execution-timeout + 10s`. `provisioning-timeout` covers
fetching the script resources and provisioning. A job still waiting for provisioning after
`provisioning-timeout` fails without consuming a retry and is retried after
`provisioning-retry-backoff`; provisioning continues in the background. A waiting job stays
active and counts against `invocation-concurrency`, so jobs waiting for new scripts can delay jobs
of scripts that are already provisioned. Retryable provisioning and
execution failures consume one retry with the same backoff; permanent ones, such as a syntax error
or a missing `linkedResources` header, fail without retries.

The only built-in provider is `fake`. It simulates provisioning with `fake.provisioning-delay` and
executes scripts in local Node.js 20+ or Python 3.10+ subprocesses discovered from `PATH`. It does
not support dependency manifests. Scripts run with the Connector Runtime operating-system identity:
this mode limits time and I/O but is **not a sandbox** and is for trusted development only.

The script must define `execute(variables, context)` and return a JSON object; its properties
become job completion variables. The task needs the `language` (`python` or `javascript`) and
`runtime` (for example `python3` or `nodejs22`) headers, and the script resource file name must
use the matching `.py` or `.js` extension.

Deployments are tracked in memory per runtime instance. Several instances do not coordinate: they
may provision the same script in parallel and rely on deterministic provider names and
create-if-absent to converge.

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
