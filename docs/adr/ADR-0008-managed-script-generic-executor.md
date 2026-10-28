# ADR-0008: Managed Script Connector with a Generic Executor per Language

## Status
Proposed

## Context

Managed scripts let a Service Task run customer Python or JavaScript in an isolated cloud function.
The script is an independent, versioned Camunda resource, linked to the task like a form or an RPA
script. Nothing happens when the resource or the BPMN is deployed: the engine resolves the link to
a `resourceKey` when it creates an `io.camunda:managed-script:1` job and passes it in the
`linkedResources` job header. Someone has to fetch the script and run it in isolation, without
running customer code in the orchestration cluster and without adding deployment-time state to the
engine.

The clusters of an environment share one AWS account or GCP project for their functions, separated
by IAM on a per-cluster name prefix. A first version created one function per script version, on
the first job that needed it. In a shared account, every version then costs a control-plane write
and one more function, against limits that cannot be raised: 60 writes per minute and 1,000
functions per GCP project, 15 control-plane calls per second per AWS account. It also made the
first job of every edit wait for provisioning, 3 to 6 seconds on AWS and 37 to 51 seconds on GCP.

## Decision

Add an optional, disabled-by-default `connector-runtime-managed-code` module with an outbound
connector, `ManagedScriptFunction`, served by the runtime like any other. Its generated element
template applies to a Service Task, links the script with `zeebe:linkedResource`, and builds the
script input with one FEEL expression mapped to `scriptInput`, the only variable the runtime
fetches.

Scripts run as data through a **generic executor**: one trusted function per language and cluster,
with no user code in it.

- When the runtime is ready, it creates the executors of its cluster if they are absent, in the
  background (`ensureExecutors`). An executor's name is the per-cluster prefix, the language and
  the digest of its wrapper, so concurrent runtime instances converge through create-if-absent, and
  a new wrapper is a new function, never an update in place.
- A job fetches the linked script by `resourceKey`, cached because a resource key's content is
  immutable, and sends it with its input and the execution timeout to the executor of its language.
- The executor writes the script to a fresh directory and runs it in a new child process, with no
  cloud credentials in its environment and bounded memory, time and output. After the call it
  kills every process it did not start with and removes every new temporary file, so nothing
  survives into the next call. It returns the execution contract response; timeouts and limits are
  reported by the wrapper as `TIMEOUT` and `RESOURCE_LIMIT`.
- No job provisions anything. A job that arrives before the executors are ready waits for the
  provisioning timeout, then fails with unchanged retries and a backoff, through
  `ConnectorRetryException` and the job retries that `JobContext` now exposes. A retryable failure
  to create the executors is retried in the background with a growing backoff; a permanent one is
  reported to the waiting jobs and tried again by a job after the backoff.
- An executor that the provider reports missing is created again once within the same job.
- Dependencies are not supported yet: a job that links a dependency manifest fails without retries.

Three providers ship. `aws` creates one zip Lambda function per language, with a log group and
its retention created first, and invokes it synchronously. `gcp` creates one Cloud Run function per
language with a request concurrency of 1, since the wrapper's cleanup would kill a concurrent
script, and invokes it through its `run.app` URL. `local` is for trusted development only and is not
a sandbox: it runs the same wrapper as a local Node.js or Python process, in a local mode that only
kills the process group of the script and uses a temporary directory of its own, since it shares
the host with other processes.

The provider SPI is one interface, `ManagedCodeProvider`, selected by its name in
`camunda.connector.managed-code.provider`:

- `ensureExecutors()` creates the executors if absent and returns once they can be invoked. It
  defaults to nothing, for a provider whose executors are deployed by an operator;
- `execute(language, request)` sends the script and its input to the executor of the language and
  returns the contract response, or `DEPLOYMENT_MISSING`.

The connector keeps everything else: fetching the script, the wait that consumes no retry, creating
the executors again on `DEPLOYMENT_MISSING`, limits and secrets. A provider can also come from a
jar added to the runtime classpath, such as `/opt/custom` in the Docker image, as one bean; this has
not been tested. The SPI has no compatibility guarantee yet.

## Consequences

### Positive

- No engine changes: linked resources already work on Service Tasks, and there are no deployment
  records, leases or reconciler.
- Editing or adding a script makes no control-plane call: a cluster has 2 functions, whatever the
  number of scripts and versions, and an edit runs at once.
- No eviction: no function belongs to a script.
- The runtime's connector machinery handles activation, variable fetching, result and error
  expressions, retries and secrets, as for every connector.
- Deployment stays fast and independent of provider availability.
- Disabling managed code leaves the ordinary connector workers unchanged.

### Negative

- Every call starts a child process: tens to a couple of hundred milliseconds in smoke runs.
- Memory, timeout and network profile are per executor, so every script of a cluster gets the same
  ones; another profile needs an executor variant, which costs functions per cluster.
- Within its own call, a script can read the wrapper's memory on Lambda, where the child cannot run
  as another user; the execution role must have no business permissions. Infosec has to review it.
- On GCP, one request per instance means `max-instance-count` caps the parallel scripts of a
  cluster per language.
- A script and its input travel in every request: at most 6 MB on a synchronous Lambda call and
  32 MB on Cloud Run.
- A wrapper upgrade creates new executors in every cluster; old ones are not deleted yet, since
  older runtime instances may still call them.
- Syntax errors surface at the first execution, not at deployment.
- Legacy `{{secrets.<name>}}` placeholders in the script input are not replaced: the connector
  implements `LegacySecretsNotResolved`, for which the runtime uses a deny-all secret filter.
  Secrets reach a script only through `camunda.secrets.<name>`, resolved by the engine.
