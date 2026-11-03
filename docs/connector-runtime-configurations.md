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
of the runtime needs the `READ` permission on the `RESOURCE` resource type. The default
`connectors` role grants it in versions that include camunda/camunda#68190; otherwise, and for a
custom role, grant it explicitly, for example an authorization of the role on `RESOURCE` with
resource ID `*` and permission `READ`. Without it, the job fails with `PLATFORM_REJECTED` and an
incident that names the permission; resolve the incident once it is granted. A runtime whose
client is not authenticated gets an incident that says so.

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

### AWS Lambda provider

The `aws` provider, in `connectors/managed-script-aws`, runs scripts through one zip Lambda function
per language and cluster: `python3.13` for `.py` scripts and `nodejs22.x` for `.js` scripts. The
module is not in the default bundle: add its jar, with its dependencies (the AWS SDK clients of
Lambda, CloudWatch Logs and STS, and the Apache 5 HTTP client), to the runtime's classpath.

```properties
camunda.connector.managed-code.enabled=true
camunda.connector.managed-code.provider=aws
camunda.connector.managed-code.deployment-name-prefix=ce-0123456789abcdef01234567-
camunda.connector.managed-code.aws.region=eu-central-1
camunda.connector.managed-code.aws.execution-role-arn=arn:aws:iam::123456789012:role/camunda-ms-executor
camunda.connector.managed-code.aws.memory-mb=512
camunda.connector.managed-code.aws.log-retention-days=30
```

- An executor is named `<deployment-name-prefix><py|js>-<12 hex of the wrapper digest>`. When it is
  absent, the provider creates its log group, `/aws/lambda/<function name>`, with the retention
  first, then the function, and waits until it is active. Concurrent runtime instances converge: one
  creation wins, the others adopt the function.
- The function timeout is `execution-timeout` plus 5 seconds, so that the wrapper reports `TIMEOUT`
  before Lambda stops it. Lambda functions run for at most 900 seconds, so startup fails when
  `execution-timeout` is above 895 seconds.
- Memory, function timeout and execution role are compared whenever the provider makes an executor
  ready, and updated in place (`UpdateFunctionConfiguration`) when they differ. A configuration change
  therefore creates no function and leaves none behind.
- `aws.region` is the region of the executors; without it, the SDK's default region chain applies
  (`AWS_REGION`, the profile). `aws.memory-mb` is between 128 and 10240 (default 512).
  `aws.log-retention-days` is a value that CloudWatch Logs accepts, such as 7, 14, 30 or 90
  (default 30).
- Credentials come from the default chain of the AWS SDK only: environment variables, a web-identity
  token file (`AWS_WEB_IDENTITY_TOKEN_FILE` and `AWS_ROLE_ARN`, as set by IRSA on EKS), the profile
  files, then the container or instance metadata. The provider works without the metadata server.
  The configuration holds no access key.
- A script is invoked synchronously, never retried by the SDK, so that it runs at most once per job
  attempt. A request above 6 MB, the script and its input as JSON, fails with `RESOURCE_LIMIT`
  before any call. A throttled invocation fails with `PLATFORM_THROTTLED`, a service error or a
  connection that could not be made with `PLATFORM_UNAVAILABLE`. A Lambda function error, such as
  the function timeout or memory, a connection lost after the request was sent, and a response
  outside the contract fail with `PLATFORM_EXECUTION_FAILED`: the script may have run. A call
  cut because the runtime stops fails with `PLATFORM_INTERRUPTED` and keeps its retries, as with
  the other providers: Lambda still runs the script to its end, so it may run again.
- A denied call, while making the executors ready or invoking them, is retried as long as the grants
  of a new cluster may still be propagating, and then fails with `PLATFORM_REJECTED`.
- The runtime never deletes an executor or its log group. After an upgrade of the wrapper, delete
  the functions and log groups under the prefix that no runtime instance uses any more.

The **execution role** is attached to the executors, which run customer code: it must grant
nothing but writing to their log groups, since a script can read the memory of the wrapper during
its call. Its trust policy lets `lambda.amazonaws.com` assume it, and its permissions are:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["logs:CreateLogStream", "logs:PutLogEvents"],
      "Resource": "arn:aws:logs:<region>:<account>:log-group:/aws/lambda/<prefix>*:*"
    }
  ]
}
```

The **identity of the runtime** needs this minimal policy, limited to the prefix of its cluster:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "lambda:CreateFunction",
        "lambda:GetFunction",
        "lambda:UpdateFunctionConfiguration",
        "lambda:InvokeFunction",
        "lambda:TagResource"
      ],
      "Resource": "arn:aws:lambda:<region>:<account>:function:<prefix>*"
    },
    {
      "Effect": "Allow",
      "Action": ["logs:CreateLogGroup", "logs:PutRetentionPolicy"],
      "Resource": "arn:aws:logs:<region>:<account>:log-group:/aws/lambda/<prefix>*"
    },
    {
      "Effect": "Allow",
      "Action": "iam:PassRole",
      "Resource": "<execution role ARN>",
      "Condition": { "StringEquals": { "iam:PassedToService": "lambda.amazonaws.com" } }
    }
  ]
}
```

### Google Cloud Run functions provider

The `gcp` provider, in `connectors/managed-script-gcp`, runs scripts through one Cloud Run function
(Cloud Functions v2 API) per language and cluster: `python313` for `.py` scripts and `nodejs22` for
`.js` scripts, each with a pinned version of the Functions Framework. The module is not in the
default bundle: add its jar, with its dependencies (the Cloud Functions client and the Google auth
library), to the runtime's classpath.

```properties
camunda.connector.managed-code.enabled=true
camunda.connector.managed-code.provider=gcp
camunda.connector.managed-code.deployment-name-prefix=ce-0123456789abcdef01234567-
camunda.connector.managed-code.gcp.project-id=my-project
camunda.connector.managed-code.gcp.region=europe-west1
camunda.connector.managed-code.gcp.function-service-account=camunda-ms-executor@my-project.iam.gserviceaccount.com
camunda.connector.managed-code.gcp.build-service-account=camunda-ms-builder@my-project.iam.gserviceaccount.com
camunda.connector.managed-code.gcp.deployer-service-account=camunda-ms-deployer@my-project.iam.gserviceaccount.com
camunda.connector.managed-code.gcp.memory=512Mi
camunda.connector.managed-code.gcp.max-instance-count=1000
```

- An executor is named `<deployment-name-prefix><py|js>-<12 hex of the wrapper digest>`. When it is
  absent, the provider uploads its source through an upload URL of the location, creates the
  function and waits until it is built and active, which takes a few minutes. Concurrent runtime
  instances converge: one creation wins, the others wait for the function. An existing executor is
  adopted without uploading its source again.
- A function serves one request at a time (request concurrency 1): after each call, the wrapper
  kills every process it did not start with, which would kill a concurrent script.
  `gcp.max-instance-count` is therefore the most scripts of a language that run at once (default
  1000, the most Cloud Run allows without a quota increase); a call beyond it fails with
  `PLATFORM_THROTTLED`. It also bounds what the executors of a cluster can cost at once.
- The function timeout is `execution-timeout` plus 5 seconds, so that the wrapper reports `TIMEOUT`
  before Cloud Run stops it, and is passed to the wrapper in `EXECUTOR_FUNCTION_TIMEOUT_MS`. HTTP
  functions run for at most 3600 seconds, so startup fails when `execution-timeout` is above 3595
  seconds.
- Memory, function timeout, `max-instance-count`, function service account, request concurrency and
  all traffic on the latest revision are compared whenever the provider makes an executor ready,
  and updated in place, as a new revision of the same function, when they differ. A configuration change therefore creates no
  function and leaves none behind.
- `gcp.project-id` and `gcp.region` locate the executors. `gcp.memory` is between `128Mi` and
  `32Gi`, in `Mi` or `Gi` (default `512Mi`).
- `gcp.function-service-account` is required: without it, Cloud Run would run the executors as the
  default compute service account, which often has Editor on the project.
- `gcp.build-service-account` is required for the same reason. The provider uploads the source of
  an executor, and Cloud Build builds its image as this account, by default the compute service
  account, which the identity of the runtime would then need to act as.
- Credentials come from the application default credentials only: the file named by
  `GOOGLE_APPLICATION_CREDENTIALS` (a service account key, or a workload identity federation
  configuration), the gcloud credentials, then the metadata server. The provider works without the
  metadata server. The configuration holds no key.
- The identity of the runtime creates the executors and calls them. With
  `gcp.deployer-service-account`, the application default credentials impersonate that service
  account, which does both instead. The identity must mint ID tokens: a service account key or the
  metadata server can, a workload identity federation configuration or a gcloud user login cannot,
  so startup fails without `gcp.deployer-service-account` for them.
- A script is posted to the `run.app` URL of its executor with an ID token of the identity, carrying
  its email, and never sent again, so that it runs at most once per job attempt. A request above
  32 MB, the script and its input as JSON, fails with `RESOURCE_LIMIT` before any call. A call
  rejected with HTTP 429 fails with `PLATFORM_THROTTLED`, with 502 or 503 with
  `PLATFORM_UNAVAILABLE`, and with 500 or 504, such as the function timeout or memory, with
  `PLATFORM_EXECUTION_FAILED`. A connection lost after the request was sent also fails with
  `PLATFORM_EXECUTION_FAILED`, since the script may have run.
- A denied call (HTTP 401 or 403, or a denied ID token), while making the executors ready or
  invoking them, is retried as long as the grants of a new cluster may still be propagating, and
  then fails with `PLATFORM_REJECTED`.
- The runtime never deletes an executor. After an upgrade of the wrapper, delete the functions under
  the prefix that no runtime instance uses any more. A function whose build failed stays failed:
  delete it, and the next job creates it again.

The project needs the Cloud Functions, Cloud Run, Cloud Build, Artifact Registry and IAM Service
Account Credentials APIs enabled.

The **function service account** runs customer code: grant it **no role**, since a script can read
the memory of the wrapper during its call. Cloud Run collects the logs of the executors without one.

The **build service account** builds the images of the executors. It needs
`roles/logging.logWriter`, `roles/artifactregistry.writer` and `roles/storage.objectViewer` on the
project, to write the build logs, push the image to the `gcf-artifacts` repository and read the
uploaded source.

The **identity of the runtime**, or the deployer service account when one is set, needs these
grants, limited to the prefix of its cluster, with `<location>` standing for
`projects/<project>/locations/<region>`. They are the ones the shared-project isolation tests of the
executor design proved together:

| Grant | Scope | Why |
|---|---|---|
| `roles/cloudfunctions.developer` | project, condition `resource.name.startsWith("<location>/functions/<prefix>")` | create, read and update its own executors |
| `roles/cloudfunctions.developer` | project, condition `resource.name.startsWith("<location>/operations/")` | poll the operations of a creation or update |
| A custom role with only `cloudfunctions.functions.generateUploadUrl` | project, condition `resource.name == "<location>"` | upload the source: the upload URL is requested on the location, where the developer role would list the functions of every cluster |
| `roles/iam.serviceAccountUser` | the function service account only | run the executors as it |
| `roles/iam.serviceAccountUser` | the build service account only | build the executors as it |
| `roles/run.invoker` | project, condition `request.host.startsWith("<prefix>")` | call its own executors through their `run.app` host; a condition on the service name has no effect |

It needs no Cloud Build, Artifact Registry, Cloud Storage or Cloud Run admin access: builds push
their images under the identity of the service. With `gcp.deployer-service-account`, the identity of
the runtime needs nothing but `roles/iam.serviceAccountTokenCreator` on the deployer service
account, to get its access and ID tokens. The invoker and impersonation grants took 66 to 91
seconds to apply, measured once: within the default `grant-propagation-window`.

With **workload identity federation**, outside GCP or without the metadata server, point
`GOOGLE_APPLICATION_CREDENTIALS` at a credential configuration that reads the token of the platform
from a file, generated without service account impersonation:

```shell
gcloud iam workload-identity-pools create-cred-config \
  projects/<project number>/locations/global/workloadIdentityPools/<pool>/providers/<provider> \
  --credential-source-file=/var/run/secrets/tokens/gcp-token \
  --output-file=/etc/camunda/gcp-credentials.json
```

Set `gcp.deployer-service-account`, and grant the federated principal, such as
`principal://iam.googleapis.com/projects/<project number>/locations/global/workloadIdentityPools/<pool>/subject/<subject>`,
`roles/iam.serviceAccountTokenCreator` on the deployer service account.

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
