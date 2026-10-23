# Connector Runtime Configurations

This page lists configuration properties you have to include to successfully connect a local Connector runtime to
the mandatory external services (Zeebe, Operate, etc.).

Overwrite the content of the `application.properties` file with the specific configuration.

> **Note:** The values below are examples only. Replace them with your own cluster-specific values, and never commit
> real client IDs or client secrets to this repository.

## Managed scripts

The managed-script connector is optional and disabled by default. When enabled, the runtime serves
`io.camunda:managed-script:1` jobs like any outbound connector: the connector fetches the script
linked to the task, provisions it through the configured provider the first time its content is
used, and invokes it. Provisioning is lazy: the first job of a new script waits for it, later jobs
reuse the deployment. See [ADR-0008](adr/ADR-0008-managed-script-lazy-provisioning.md).

The element template `managed-script-connector.json`, generated in
`connector-runtime/connector-runtime-managed-code/element-templates`, applies to a Service Task. It
links the `.py` or `.js` script with `zeebe:linkedResource` (`latest` binding by default) and
builds the script input with one FEEL expression, mapped to the `scriptInput` variable, for example
`={a: order.amount, b: tax}`. The runtime fetches only that variable, so the script receives no
other process variable. The script must define `execute(variables, context)` and return a JSON
object, which the result variable or result expression of the task maps, as for other connectors.
The language and runtime follow the file extension; the optional `language` and `runtime` task
headers override them.

```properties
camunda.connector.managed-code.enabled=true
camunda.connector.managed-code.provider=local
camunda.connector.managed-code.deployment-name-prefix=camunda-ms-
camunda.connector.managed-code.execution-timeout=30s
camunda.connector.managed-code.provisioning-timeout=2m
camunda.connector.managed-code.provisioning-retry-backoff=10s
camunda.connector.managed-code.provisioning-concurrency=2
camunda.connector.managed-code.local.directory=/tmp/camunda-managed-code
camunda.connector.managed-code.local.install-timeout=5m
```

`deployment-name-prefix` starts every provider resource name, one prefix per cluster, so that the
identity of the runtime can be limited to its own resources. It has at most 31 lowercase letters,
digits and hyphens, followed by a 32-character hash. In a cloud account shared by several clusters,
use prefixes of one fixed length that are not prefixes of each other, such as `ce-` followed by 24
hexadecimal characters and `-`: with `a-` and `a-b-`, the identity of the first cluster could
create names of the second.

The job timeout must cover `provisioning-timeout + execution-timeout`. The default job timeout of
the runtime, 5 minutes, does; set `CONNECTOR_MANAGED_SCRIPT_TIMEOUT` (milliseconds) or the job
timeout of the task otherwise. `provisioning-timeout` covers fetching the script resources and
provisioning. A job still waiting for provisioning after `provisioning-timeout` fails without
consuming a retry and is retried after `provisioning-retry-backoff`; provisioning continues in the
background. A waiting job stays active and counts against the active jobs of the managed-script
worker (`camunda.client.worker.defaults.max-jobs-active`, 100 in the bundle), so jobs waiting for
new scripts can delay jobs of scripts that are already provisioned; jobs of other connectors have
their own worker. Retryable provisioning and execution failures consume
one retry with the same backoff; permanent ones, such as a syntax error or a missing
`linkedResources` header, fail without retries.

Unlike other connectors, the runtime does not replace legacy `{{secrets.<name>}}` placeholders in
the script input: the script receives them as literal text. A script gets a secret only through
`camunda.secrets.<name>` in its input mapping, which the engine resolves.

The `local` provider deploys each script to its own subdirectory of
`local.directory` (by default `camunda-managed-code` in the Java temporary directory), adopts
existing deployments after a restart, and executes scripts in local Node.js 20+ or Python 3.10+
subprocesses discovered from `PATH`. A linked `requirements.txt` or `package.json` is installed
once while provisioning, with `pip install --target` or `npm install --ignore-scripts`, bounded by
`local.install-timeout`. Installation uses the host environment, so proxy and registry settings
apply. Scripts run with the Connector Runtime operating-system identity: this mode limits time and
I/O but is **not a sandbox** and is for trusted development only.

The `aws` provider deploys each script as its own AWS Lambda function, named after the artifact
behind `deployment-name-prefix`, and invokes a published version of it:

```properties
camunda.connector.managed-code.provider=aws
camunda.connector.managed-code.aws.region=eu-central-1
camunda.connector.managed-code.aws.execution-role-arn=arn:aws:iam::<account>:role/<execution-role>
camunda.connector.managed-code.aws.memory-mb=256
camunda.connector.managed-code.aws.log-retention-days=14
camunda.connector.managed-code.aws.poll-interval=2s
```

Credentials come from the default AWS credentials chain, for example `AWS_PROFILE` or a workload
identity. That identity needs:

- `lambda:CreateFunction`, `GetFunction`, `PublishVersion`, `InvokeFunction`, `DeleteFunction` and
  `TagResource` on `function:<prefix>*`;
- `logs:CreateLogGroup`, `logs:PutRetentionPolicy` and `logs:DeleteLogGroup` on
  `log-group:/aws/lambda/<prefix>*`;
- `iam:PassRole` on the execution role only, conditioned on `iam:PassedToService` being
  `lambda.amazonaws.com`.

It needs no `lambda:ListFunctions`, which cannot be limited to a prefix and returns the environment
variables of every function in the account. The provider creates the log group of each function
with `log-retention-days` before the function, and deletes it after the function. The execution
role runs customer code and must grant nothing beyond `logs:CreateLogStream` and
`logs:PutLogEvents` on its log group. Status is read with `GetFunction`, which has its own quota,
every `poll-interval` with jitter; a throttled read is retried until `provisioning-timeout`. The
function timeout is `execution-timeout`. A function is created with the
first job of its script, which waits until it is active; later jobs invoke the published version.
Concurrent instances converge on one function: a create that conflicts adopts the existing one.
Dependencies are not supported. Networking, such as denying egress, is not configured by the
provider.

The `gcp` provider deploys each script as its own Google Cloud Run function (Cloud Functions v2
API), named after the artifact behind `deployment-name-prefix`, and invokes it through its
`run.app` URL:

```properties
camunda.connector.managed-code.provider=gcp
camunda.connector.managed-code.gcp.project=<project>
camunda.connector.managed-code.gcp.region=europe-west1
camunda.connector.managed-code.gcp.function-service-account=<function-identity>@<project>.iam.gserviceaccount.com
camunda.connector.managed-code.gcp.deployer-service-account=<deployer>@<project>.iam.gserviceaccount.com
camunda.connector.managed-code.gcp.memory-mb=256
camunda.connector.managed-code.gcp.max-instance-count=10
camunda.connector.managed-code.gcp.poll-interval=5s
```

Credentials come from the application default credentials, for example GKE Workload Identity. The
runtime impersonates `deployer-service-account` (`roles/iam.serviceAccountTokenCreator` on it),
which also mints the ID tokens of invocations. Set it under GKE Workload Identity: the federated
identity cannot mint those ID tokens itself. Without it, the default credentials are used as they
are, which works only when they are a real service account, such as a key file in development. The deployer needs, for a prefix `<prefix>` in project `<p>` and region
`<r>`:

- `roles/cloudfunctions.developer` conditioned on
  `resource.name.startsWith("projects/<p>/locations/<r>/functions/<prefix>")`, and again on
  `.../operations/`;
- a custom role with only `cloudfunctions.functions.generateUploadUrl`, conditioned on
  `resource.name == "projects/<p>/locations/<r>"`. The developer role on the location would let it
  list the functions of every cluster;
- `roles/iam.serviceAccountUser` on the function service account only;
- `roles/run.invoker` on the project, conditioned on `request.host.startsWith("<prefix>")`.

It needs no listing, no `roles/logging.viewer` and no Cloud Build, Artifact Registry or storage
access. The ID token includes the email claim, which the conditioned invoker binding needs. The
function service account holds no roles; it is required, since the platform would otherwise run
functions as the default Compute Engine service account. Every create sets `memory-mb`,
`execution-timeout` as the function timeout, `max-instance-count` and one request per instance. An
existing function is adopted without uploading its source again, so concurrent instances spend one
write of the 60 per minute that the project allows. Permission denials are retried, since new grants
take about 2 minutes to apply; a denial still present after half of `provisioning-timeout` fails the
attempt, so that a wrong binding consumes the job's retries and ends in an incident. Python scripts
are built with the pinned Functions Framework of the platform; Node.js scripts get no `package.json` dependencies. Dependencies of the script are not
supported, and networking, such as denying egress, is not configured by the provider.

Other providers implement `ManagedCodeProvider`, which packages the script for the platform,
creates, invokes and deletes it, and register it as a Spring bean, for example through an
auto-configuration in a jar placed in `/opt/custom` of the Docker image. The connector uses the
bean whose `name()` matches `provider`. Loading a provider from `/opt/custom` has not been tested
yet.

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
