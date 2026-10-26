# Connector Runtime Configurations

This page lists configuration properties you have to include to successfully connect a local Connector runtime to
the mandatory external services (Zeebe, Operate, etc.).

Overwrite the content of the `application.properties` file with the specific configuration.

> **Note:** The values below are examples only. Replace them with your own cluster-specific values, and never commit
> real client IDs or client secrets to this repository.

## Managed scripts

The managed-script connector is optional and disabled by default. When enabled, the runtime serves
`io.camunda:managed-script:1` jobs like any outbound connector: the connector fetches the script
linked to the task and sends it, with its input, to a generic executor of its language. The
executors, one function per language and cluster, are created when the runtime is ready; a script
creates no function. See [ADR-0008](adr/ADR-0008-managed-script-generic-executor.md).

The element template `managed-script-connector.json`, generated in
`connector-runtime/connector-runtime-managed-code/element-templates`, applies to a Service Task. It
links the `.py` or `.js` script with `zeebe:linkedResource` (`latest` binding by default) and
builds the script input with one FEEL expression, mapped to the `scriptInput` variable, for example
`={a: order.amount, b: tax}`. The runtime fetches only that variable, so the script receives no
other process variable. The script must define `execute(variables, context)` and return a JSON
object, which the result variable or result expression of the task maps, as for other connectors.
The language follows the file extension; the optional `language` task header overrides it. Every
script of a language runs on its executor, Python 3.13 or Node.js 22, so a `runtime` header can only
name that runtime. Dependencies are not supported: a task that links a dependency manifest fails.

```properties
camunda.connector.managed-code.enabled=true
camunda.connector.managed-code.provider=local
camunda.connector.managed-code.deployment-name-prefix=camunda-ms-
camunda.connector.managed-code.execution-timeout=30s
camunda.connector.managed-code.provisioning-timeout=2m
camunda.connector.managed-code.provisioning-retry-backoff=10s
camunda.connector.managed-code.local.directory=/tmp/camunda-managed-code
```

`deployment-name-prefix` starts every provider resource name, one prefix per cluster, so that the
identity of the runtime can be limited to its own resources. It has at most 48 lowercase letters,
digits and hyphens, followed by `py-` or `js-` and 12 characters of the wrapper digest. In a cloud
account shared by several clusters, use prefixes of one fixed length that are not prefixes of each
other, such as `ce-` followed by 24 hexadecimal characters and `-`: with `a-` and `a-b-`, the
identity of the first cluster could create names of the second.

The job timeout must cover `provisioning-timeout + execution-timeout`. The default job timeout of
the runtime, 5 minutes, does; set `CONNECTOR_MANAGED_SCRIPT_TIMEOUT` (milliseconds) or the job
timeout of the task otherwise. `provisioning-timeout` covers fetching the script and waiting for the
executors. A job that arrives before the executors are ready, for example right after the first
start of a cluster, fails after `provisioning-timeout` without consuming a retry and is retried
after `provisioning-retry-backoff`; the executors keep being created in the background. A
retryable failure to create them is retried with a backoff that starts at
`provisioning-retry-backoff` and grows to a minute; a permanent one, such as a missing permission,
fails the waiting jobs without retries and is tried again by a job after the backoff.
`execution-timeout` is the deadline of a script, enforced by the executor, which reports `TIMEOUT`.
Retryable execution failures consume one retry with the same backoff; permanent ones, such as a
syntax error or a missing `linkedResources` header, fail without retries.

Unlike other connectors, the runtime does not replace legacy `{{secrets.<name>}}` placeholders in
the script input: the script receives them as literal text. A script gets a secret only through
`camunda.secrets.<name>` in its input mapping, which the engine resolves.

The `local` provider writes the executor wrapper of each language to a subdirectory of
`local.directory` (by default `camunda-managed-code` in the Java temporary directory) and starts it
for every call with local Node.js 20+ or Python 3.10+, discovered from `PATH`. The wrapper runs in a
local mode: it kills only the process group of the script and uses a temporary directory of its
own, removed after the call. Scripts run with the Connector Runtime operating-system identity: this
mode limits time and output but is **not a sandbox** and is for trusted development only. It is
tested on macOS and Linux, not on Windows.

The `aws` provider creates one AWS Lambda function per language, named
`<deployment-name-prefix><py|js>-<digest>`, and invokes it synchronously:

```properties
camunda.connector.managed-code.provider=aws
camunda.connector.managed-code.aws.region=eu-central-1
camunda.connector.managed-code.aws.execution-role-arn=arn:aws:iam::<account>:role/<execution-role>
camunda.connector.managed-code.aws.memory-mb=512
camunda.connector.managed-code.aws.log-retention-days=14
camunda.connector.managed-code.aws.poll-interval=2s
```

Credentials come from the default AWS credentials chain, for example `AWS_PROFILE` or a workload
identity. That identity needs:

- `lambda:CreateFunction`, `GetFunction`, `InvokeFunction` and `TagResource` on
  `function:<prefix>*`;
- `logs:CreateLogGroup` and `logs:PutRetentionPolicy` on `log-group:/aws/lambda/<prefix>*`;
- `iam:PassRole` on the execution role only, conditioned on `iam:PassedToService` being
  `lambda.amazonaws.com`.

It needs no `lambda:ListFunctions`, which cannot be limited to a prefix and returns the environment
variables of every function in the account. The provider creates the log group of each executor
with `log-retention-days` before the function. The execution role is attached to the executor,
which runs customer code, and must grant nothing beyond `logs:CreateLogStream` and
`logs:PutLogEvents` on its log group. Functions are zip packages: Lambda pulls a container image
with its own identity, so an image would not be checked against the calling cluster. Status is read
with `GetFunction`, which has its own quota, every `poll-interval` with jitter. The function timeout
is `execution-timeout` plus 5 seconds, so that the wrapper ends a late script itself. A script and
its input are limited to the 6 MB of a synchronous invocation. Concurrent instances converge on one
executor: a create that conflicts adopts the existing one. A runtime that finds its executors only
reads. Networking, such as denying egress, is not configured by the provider.

The `gcp` provider creates one Google Cloud Run function (Cloud Functions v2 API) per language,
named `<deployment-name-prefix><py|js>-<digest>`, and invokes it through its `run.app` URL:

```properties
camunda.connector.managed-code.provider=gcp
camunda.connector.managed-code.gcp.project=<project>
camunda.connector.managed-code.gcp.region=europe-west1
camunda.connector.managed-code.gcp.function-service-account=<function-identity>@<project>.iam.gserviceaccount.com
camunda.connector.managed-code.gcp.deployer-service-account=<deployer>@<project>.iam.gserviceaccount.com
camunda.connector.managed-code.gcp.memory-mb=512
camunda.connector.managed-code.gcp.max-instance-count=10
camunda.connector.managed-code.gcp.poll-interval=5s
```

Credentials come from the application default credentials, for example GKE Workload Identity. The
runtime impersonates `deployer-service-account` (`roles/iam.serviceAccountTokenCreator` on it),
which also mints the ID tokens of invocations. Set it under GKE Workload Identity: the federated
identity cannot mint those ID tokens itself. Without it, the default credentials are used as they
are, which works only when they are a real service account, such as a key file in development. The
deployer needs, for a prefix `<prefix>` in project `<p>` and region `<r>`:

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
`execution-timeout` plus 5 seconds as the function timeout, `max-instance-count` and one request
per instance: the wrapper kills every process it did not start with after each call, so
`max-instance-count` is also the maximum of parallel scripts of the cluster per language. An
existing executor is adopted without uploading its source again, so concurrent instances spend one
write of the 60 per minute that the project allows. Permission denials are retried, since new grants
take about 2 minutes to apply; a denial still present after half of `provisioning-timeout` fails the
attempt. The executors are built with the pinned Functions Framework. A script and its input are
limited to the 32 MB of a Cloud Run request. Networking, such as denying egress, is not configured
by the provider.

Old executors are not deleted when a new runtime version brings a new wrapper: older runtime
instances may still call them.

Other providers implement `ManagedCodeProvider`, which creates the executors of a cluster and runs
a script on them, and register it as a Spring bean, for example through an auto-configuration in a
jar placed in `/opt/custom` of the Docker image. The connector uses the bean whose `name()` matches
`provider`. Loading a provider from `/opt/custom` has not been tested yet.

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
