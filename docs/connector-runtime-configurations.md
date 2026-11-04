# Connector Runtime Configurations

This page lists configuration properties you have to include to successfully connect a local Connector runtime to
the mandatory external services (Zeebe, Operate, etc.).

Overwrite the content of the `application.properties` file with the specific configuration.

> **Note:** The values below are examples only. Replace them with your own cluster-specific values, and never commit
> real client IDs or client secrets to this repository.

## Managed scripts

The managed-script connector (`connectors/managed-script`, part of the default bundle) is optional
and disabled by default. When enabled, the runtime serves
`io.camunda:managed-script:1` jobs like any outbound connector: the connector fetches the script
linked to the task and sends it, with its input, to the generic executor of its language through a
managed-code provider. See [ADR-0008](adr/ADR-0008-managed-script-generic-executor.md).

The element template `managed-script-connector.json`, generated in
`connectors/managed-script/element-templates`, applies to a Service Task. It
links the `.py` or `.js` script with `zeebe:linkedResource` (`latest` binding by default) and
builds the script input with one FEEL expression, mapped to the `scriptInput` variable, for example
`={a: order.amount, b: tax}`. The runtime fetches only that variable, so the script receives no
other process variable. The language follows the file extension only. Dependencies are not
supported: a task that links a `dependencies` resource fails with `UNSUPPORTED_DEPENDENCIES`.

The runtime reads the script through the resource API of the Orchestration Cluster, so the client
of the runtime needs the `READ` permission on the `RESOURCE` resource type, which the default
`connectors` role does not grant. Without it, the job fails with `PLATFORM_REJECTED` and an incident
that names the permission; resolve the incident once it is granted.

```properties
camunda.connector.managed-code.enabled=true
camunda.connector.managed-code.provider=<provider name>
camunda.connector.managed-code.deployment-name-prefix=camunda-ms-
camunda.connector.managed-code.execution-timeout=30s
camunda.connector.managed-code.provisioning-timeout=2m
camunda.connector.managed-code.grant-propagation-window=2m
camunda.connector.managed-code.provisioning-retry-backoff=10s
```

- `provider` names the managed-code provider. A provider implements the SPI of
  `connector-managed-script-api` (Apache 2.0) and ships in a module of its own, added to the
  runtime's classpath; startup fails when managed code is enabled and no provider, or more than
  one, has that name.
- `deployment-name-prefix` starts every executor name, one prefix per cluster, so that the identity
  of the runtime can be limited to its own executors. It has at most 48 lowercase letters, digits
  and hyphens, followed by `py-` or `js-` and 12 characters of the wrapper digest. In a cloud account
  shared by several clusters, use prefixes of one fixed length that are not prefixes of each other,
  such as `ce-` followed by 24 hexadecimal characters and `-`.
- `execution-timeout` is the deadline of a script, enforced by the executor, which reports
  `TIMEOUT`.
- `provisioning-timeout` is how long a job waits for the executor of its language, including to
  make it ready again once when the provider reports it missing. The job then fails with
  `PROVISIONING_PENDING`, its retries unchanged. Once the runtime is ready, it makes the executors
  ready in the background, each language on its own, so that a failing Python executor does not
  hold back JavaScript jobs; a job that arrives earlier starts it. A transient failure is tried
  again with a backoff that doubles up to 1 minute. When it has lasted longer than twice
  `provisioning-timeout`, waiting jobs fail with it and consume their retries, so that a lasting
  failure ends in an incident. A permanent failure fails waiting jobs without retries, and the
  first job after `provisioning-retry-backoff` tries again.
- `grant-propagation-window` is how long after the first attempt of the runtime a permission denial
  is taken for the grants of a new cluster that still propagate (IAM grants took 66 to 98 seconds,
  measured once): it is tried again, and waiting jobs fail with `PROVISIONING_PENDING`. After the
  window, a denial is permanent (`PLATFORM_REJECTED`). `0s` turns it off.
- `provisioning-retry-backoff` is the backoff of a job that failed because of the platform and
  keeps its retries (`PROVISIONING_PENDING`, `PLATFORM_INTERRUPTED`), whatever the task's
  `retryBackoff` header says. A failure that consumes a retry uses the task's `retryBackoff`.

### Local provider

The `local` provider, part of `connectors/managed-script`, runs scripts on the host of the runtime,
with the interpreter installed there: Node.js 20 or later for `.js` scripts, Python 3.10 or later
(`python3`) for `.py` scripts. It is for **trusted development only and is not a sandbox**: a script
runs with the permissions of the user of the runtime, so use it to try a script without a cloud
account, not in production. It logs a warning at startup. Windows is not supported.

```properties
camunda.connector.managed-code.enabled=true
camunda.connector.managed-code.provider=local
camunda.connector.managed-code.local.directory=/home/me/.camunda/managed-script
```

- `local.directory` is where the provider writes the wrapper files, in a directory named after their
  digest (such as `js-0123456789ab`), and creates the temporary directories of the calls. It is
  private to the user of the runtime: when it prepares a language, the provider refuses a directory
  or wrapper file that is a link, belongs to another user or can be written by others, because the
  files in it are run. It does not check the parent directories: choose a directory whose parents
  other users cannot write to either, or that only the user of the runtime can enter. The default is
  `.camunda/managed-script` in the user's home directory. If a file there does not match the wrapper
  of this runtime, the provider refuses to start the language instead of running it: remove the
  directory.
- What a script writes with `console` or `print`, up to 4 KB per call, is logged at `INFO` by the
  logger `io.camunda.connector.managedscript.local.script`, apart from the provider's own logs, so
  that you can route or silence it. It is not part of the result or of an incident.
- A language needs only its own interpreter. The provider finds `node` and `python3` on the `PATH`
  and starts the real interpreter behind a version-manager shim such as asdf's, which only works from
  the directory it was set up for. A host with only Node.js runs `.js` scripts, and a `.py` script
  fails with a message saying that Python 3.10 or later was not found, until you install it: the
  first job after `provisioning-retry-backoff` looks again, so the runtime does not need a restart.

The job timeout of a managed-script task must cover `provisioning-timeout` and `execution-timeout`,
plus a few seconds for the call, so that a script still running does not get its job activated
again. The job worker's default job timeout (5 minutes, `camunda.client.worker.defaults.timeout`)
covers the defaults; set the job timeout of the task when you raise either.

Each failure has an error code, and the code decides what happens to the job's retries
(ADR-0008):

- no retries left, so an incident: script errors, timeouts, limits, invalid jobs or results, and
  permanent platform failures (`PLATFORM_REJECTED`, `PLATFORM_EXECUTION_FAILED`);
- one retry consumed, using the task's `retryBackoff` header: transient platform failures
  (`PLATFORM_THROTTLED`, `PLATFORM_UNAVAILABLE`, `PLATFORM_ERROR`) and a missing executor
  (`DEPLOYMENT_MISSING`);
- retries unchanged, after `provisioning-retry-backoff`: `PROVISIONING_PENDING` and
  `PLATFORM_INTERRUPTED`.

The incident shows the error message, and the script's stack trace when the script raised the
failure. Both also reach the runtime's error log, so a script must not let a sensitive value reach
an exception message. Secrets in the script input are resolved as for any connector.

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
