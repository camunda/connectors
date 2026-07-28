# ADR-0006: Long-Term Strategy for the AWS e2e Test Lane's LocalStack Dependency

## Status
Accepted

## Context

The AWS e2e test lane (`connectors-e2e-test/connectors-e2e-test-aws/**`) pulls `localstack/localstack:4.8` directly from Docker Hub. The reference is declared in `connectors-e2e-test-aws-base/src/test/resources/docker-images.properties`, resolved verbatim by `DockerImages.get("localstack")`, and handed to Testcontainers' `LocalStackContainer`, which pulls it from `docker.io`.

Five e2e suites depend on this tag being pullable on every CI run: `AwsDynamoDbTest`, `AwsEventBridgeTest`, `AwsLambdaTest`, `AwsSnsTest`, `AwsSqsTest` (plus shared setup in `BaseAwsTest`). If the tag becomes unpullable, all five fail to start a container — a CI-lane outage, not a test failure — with no fallback in place today.

### LocalStack's licensing change (verified against LocalStack's own site, 2026-07-28)

- LocalStack ended support for the LocalStack for AWS Community edition on **March 23, 2026**. From that date, LocalStack ships a single image on Docker Hub that requires a user account and an auth token **to run** (the gate is at container start, via `LOCALSTACK_AUTH_TOKEN` — not at `docker pull`; the image itself stays publicly pullable).
- A temporary bypass (`LOCALSTACK_ACKNOWLEDGE_ACCOUNT_REQUIREMENT=1`) was available but expired **April 6, 2026**.
- Our pinned `4.8` tag predates this change and is unaffected by it: it does not require an account or token to run. Checked against Docker Hub's API today: `localstack/localstack:4.8` has `tag_status: active`, was last pushed 2025-09-16, and was last pulled 2026-07-28 — it is still published and pullable. Any *upgrade* past `4.8`, however, would pull an image that requires an account and token.
- LocalStack's current commercial tiers are Base, Ultimate, and Enterprise; the free "Hobby" tier is for non-commercial use only. Separately, LocalStack also offers free subscriptions for verified students and for open-source projects (subject to LocalStack's approval) — see Recommendation below.

**Net effect today:** the lane is not currently broken — `4.8` still pulls and runs without a token — but it is a dependency on a specific historical tag with no mirror, no fallback, and no monitoring for the day it stops being served. Bumping past `4.8` is no longer a drop-in version change; it now also requires provisioning a LocalStack account/token.

### A relevant existing precedent

`connectors-e2e-test-agentic-ai/src/test/resources/docker-images.properties` already pulls `registry.camunda.cloud/mcp/mcp-test-server:2.2.0`, so a Camunda-controlled registry is already reachable from this repo's CI. That image is Camunda-authored, though — mirroring `localstack/localstack:4.8` means periodically re-pushing an unmodified third-party image and having a process to notice when it needs re-syncing, which doesn't exist today for any image in this repo.

## Decision

The pinned tag `localstack/localstack:4.8` is left unchanged — bumping it is a separate decision, out of scope here. This ADR records the risk above and the options below; adopting one of them is a follow-up.

No code change is needed to adopt a mirror later. Testcontainers 2.0.5 (the version pinned in `parent/pom.xml`) already substitutes image names via `TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX` (or the `hub.image.name.prefix` config property), active by default through `DefaultImageNameSubstitutor`. Setting that variable in CI prefixes every Docker Hub image reference — including ones resolved through specialized Testcontainers classes like `LocalStackContainer` and `KafkaContainer`, not just plain `GenericContainer` — before the pull, and it leaves already-registry-qualified references (e.g. `quay.io/keycloak/keycloak:26.5`, `registry.camunda.cloud/...`) untouched. A trailing slash on the prefix value is required (`registry.camunda.cloud/mirror/`, not `registry.camunda.cloud/mirror`).

Because the substitution is global, adopting a mirror this way means the mirror needs to hold every Docker Hub image the AWS/kafka/rabbitmq/jdbc e2e lanes pull — not only `localstack/localstack:4.8` — including Testcontainers' own Ryuk reaper image.

### Option (a): Mirror the pinned tag to an internal registry

Push `localstack/localstack:4.8` to an internal registry (e.g. `registry.camunda.cloud`, already reachable from CI per the precedent above) and set `TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX` in the e2e workflows (`E2E_BRANCH_RUN.yml`, `NIGHTLY_E2E.yml`).

- **Pro:** Freezes the exact image this suite is validated against; no longer depends on the tag remaining on Docker Hub.
- **Con:** Requires someone with registry push credentials to create and maintain the mirror — not done as part of this ADR. Requires an ongoing refresh process, or the mirror becomes a second, unmonitored stale dependency. Does not cover `AwsLambdaTest` fully: LocalStack's Lambda execution pulls runtime images itself, from inside the container via the mounted Docker socket (see `BaseAwsTest`'s `LAMBDA_RUNTIME_ENVIRONMENT_TIMEOUT` and `AwsTestHelper.removeLambdaContainers`) — those pulls bypass Testcontainers and this substitution mechanism entirely. A Docker-daemon-level registry mirror (`registry-mirrors` in `daemon.json`, or a CI-level pull-through cache) would cover this case too, and is a variant of this option worth considering alongside the Testcontainers-level prefix.

### Option (b): Per-service OSS emulators

Replace LocalStack with dedicated open-source emulators per AWS service (e.g. DynamoDB Local).

- **Pro:** No single vendor's licensing decision can take down the whole lane at once.
- **Con:** No single tool covers SQS, SNS, EventBridge, Lambda and DynamoDB the way LocalStack does; this likely means adopting three-plus emulator projects, each with its own setup and update cadence.

### Option (c): Paid LocalStack license (Ultimate)

- **Pro:** Stays current with upstream, keeps single-tool coverage, and — specifically at the **Ultimate** tier, not Base — additionally covers services the free tier doesn't emulate at all (e.g. Bedrock, SageMaker, Textract; confirmed against LocalStack's coverage matrix). Base does not include these.
- **Con:** Recurring cost with an owner who has to justify and renew it.

### Option (d): Apply for LocalStack's free open-source license

LocalStack offers a free subscription for open-source projects, subject to LocalStack's approval (application link on their licensing page). `camunda/connectors` is Apache-2.0.

- **Pro:** Potentially the coverage of a paid tier at no cost.
- **Con:** Approval is at LocalStack's discretion; eligibility for a commercial vendor's open-source repo is untested and would need to be asked. Not evaluated further here — listed as an open item for whoever picks up this ADR's follow-up.

### Recommendation (non-binding)

Lean toward (a) as the low-cost immediate mitigation — mirroring the already-validated tag costs one push plus a periodic-refresh task, and `registry.camunda.cloud` is already integrated into CI — while separately checking eligibility for (d), which could make (c)'s coverage available at no recurring cost. Revisit (c) if e2e coverage grows into services the free tier never emulates. This is a recommendation; the actual choice is a team decision.

## Consequences

### Positive
- The risk is now written down instead of implicit, and the current state (still working, not yet broken) is distinguished from the future risk (any version bump requires a LocalStack account/token).
- Adopting a mirror requires no code change — just setting `TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX` in the e2e workflows once a mirror exists.
- The comparison of (a)/(b)/(c)/(d) is available for the team to act on.

### Negative
- The long-term choice remains open; this ADR does not reduce the underlying risk until a human mirrors an image, adopts new emulators, purchases a license, or secures an OSS grant.
- `TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX` prefixes every Docker Hub pull in the JVM it's set for, not just LocalStack — a mirror needs to be provisioned with that scope in mind.
- Option (a) as scoped (Testcontainers-level prefix) does not cover LocalStack's own Lambda runtime-image pulls; see the Con under option (a).
