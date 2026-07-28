# ADR-0006: Long-Term Strategy for the AWS e2e Test Lane's LocalStack Dependency

## Status
Proposed

This ADR bundles two things with different statuses:

- **Already decided and shipped in this PR:** keep the pinned `localstack/localstack:4.8` tag as-is, add an optional registry-override hook to `DockerImages` so a mirror can be adopted later with zero code changes, and record the risk below so it isn't silently rediscovered.
- **Still open, and out of scope for this PR to resolve:** which long-term strategy (below) replaces today's direct-from-Docker-Hub pull. That choice affects the whole AWS e2e lane and needs a human/team decision, not an agent's; the status stays `Proposed` until the team picks one and a follow-up implements it.

## Context

The AWS e2e test lane (`connectors-e2e-test/connectors-e2e-test-aws/**`) pulls `localstack/localstack:4.8` directly from Docker Hub. The reference is declared in `connectors-e2e-test-aws-base/src/test/resources/docker-images.properties` and resolved verbatim by `DockerImages.get("localstack")` (a plain `Properties` lookup with no registry-indirection layer today), then handed to Testcontainers, which pulls it straight from `docker.io`.

Five e2e suites currently depend on this single pinned tag being pullable on every CI run: `AwsDynamoDbTest`, `AwsEventBridgeTest`, `AwsLambdaTest`, `AwsSnsTest`, `AwsSqsTest` (plus shared setup in `BaseAwsTest`). If `localstack/localstack:4.8` becomes unavailable on Docker Hub, all five suites fail to even start a container — not a test assertion failure but a hard CI-lane outage — with no fallback.

As of this writing (per the originating issue, camunda/connectors#7976), the risk is:

- LocalStack's community/open-source edition has an announced end-of-life around March 2026. What happens to already-published historical tags (including `4.8`) after that date — whether they stay on Docker Hub indefinitely or are pulled/archived — is not something this ADR can verify from the repository; it is a vendor decision outside our control.
- Current LocalStack releases reportedly require an account/auth token to pull, and the free "Hobby" tier's license is stated to be non-commercial-use only. If accurate, this repo's CI usage (a commercial company's test suite) would not cleanly fit that tier going forward, independent of the EOL timeline.
- Net effect: the AWS e2e lane has a silent dependency on a specific EOL image tag with no mirror, no fallback, and no monitoring for "this tag just disappeared."

These claims about LocalStack's licensing and EOL timeline come from the issue description, not from re-verification against LocalStack's current terms by the author of this ADR — they should be re-confirmed against LocalStack's official site/pricing page before this ADR is finalized or acted upon by a human.

### A relevant existing precedent

`connectors-e2e-test-agentic-ai/src/test/resources/docker-images.properties` already pulls `registry.camunda.cloud/mcp/mcp-test-server:2.2.0` — i.e., a Camunda-controlled registry is already reachable from this repo's CI and already has *some* image published to it. That lowers the incremental cost of option (a) below: the destination registry likely doesn't need to be stood up from scratch, since Camunda already publishes to `registry.camunda.cloud`. The important distinction: `mcp-test-server` is a Camunda-*authored* image (Camunda controls the source and publishes new versions directly), whereas mirroring `localstack/localstack:4.8` means periodically re-pushing an unmodified *third-party* image and having a process to notice when the upstream needs to be re-synced. That push/refresh process does not exist in this repo today, for any image — this ADR treats it as new work regardless of which registry hosts the result. No other `docker-images.properties` file in this repo (kafka, rabbitmq, jdbc, http-client) uses a registry-prefix or mirroring convention for third-party images.

## Decision

**Shipped now (this PR):**

- The pinned tag `localstack/localstack:4.8` is left unchanged. Changing it is an unrelated decision (see AGENTS.md guidance against bundling unrelated changes) and is explicitly out of scope here.
- `DockerImages.get(String)` gained an optional registry-prefix override: if the environment variable `CONNECTORS_TEST_IMAGE_REGISTRY` is set to a non-blank value, its value is prepended to every resolved image reference before it's returned; if unset, behavior is byte-for-byte identical to today. See `connector-commons/connector-test-utils/src/main/java/io/camunda/connector/test/utils/DockerImages.java`. This makes adopting any of the options below — but especially (a) — a one-environment-variable operation for whoever implements it, without this PR fabricating that a mirror exists or performing any registry push (no push access from this environment, and none was attempted).

**Not decided by this PR — for the team to choose:**

### Option (a): Mirror the pinned tag to an internal registry

Push `localstack/localstack:4.8` (the exact version already tested against) to an internal registry — e.g. `registry.camunda.cloud`, which per the precedent above is already reachable from CI — and point `docker-images.properties` at the mirrored reference (or rely on the `CONNECTORS_TEST_IMAGE_REGISTRY` override added in this PR).

- **Pro:** Freezes the exact image this suite is validated against; immune to the upstream tag disappearing or LocalStack changing pull requirements for new pulls.
- **Con:** Requires a human with registry push credentials to perform the initial mirror (not achievable from this environment). Requires an ongoing process — someone/something has to notice if `4.8` is ever bumped, or the mirror silently becomes "the version we test" forever while the rest of the ecosystem moves on. A stale mirror that nobody revisits is a slow-motion version of the same "silently depending on something nobody's watching" problem this ADR exists to fix, just one layer removed.

### Option (b): Per-service OSS emulators

Replace LocalStack with dedicated open-source emulators per AWS service — e.g. DynamoDB Local for DynamoDB.

- **Pro:** No single vendor's licensing/EOL decision can take down the whole lane at once; each emulator is independently maintained.
- **Con:** No single tool covers SQS, SNS, and EventBridge the way LocalStack does today — this repo's AWS e2e lane exercises DynamoDB, EventBridge, Lambda, SNS, and SQS (five suites), so this option likely means adopting three-plus different emulator projects, each with its own Testcontainers setup, its own quirks, and its own update cadence. More test-infrastructure surface area and more images to independently track for the same "is this still maintained" risk this ADR is about.

### Option (c): Paid LocalStack license

Purchase a commercial LocalStack license.

- **Pro:** Stays current with upstream by construction (no stale mirror risk), keeps the single-tool coverage across all five AWS services this lane already exercises, and additionally covers services the free tier does not emulate at all today (e.g. Bedrock, Comprehend, Textract, SageMaker per LocalStack's tier documentation) — relevant if AWS e2e coverage grows into connectors backed by those services.
- **Con:** Recurring cost with an owner who has to justify and renew it; introduces a paid-vendor dependency for a test lane that today has none.

### Recommendation (non-binding)

Leaning toward (a) as the immediate, low-cost mitigation — mirroring exactly the tag already validated against costs one push plus a lightweight periodic-refresh task, and `registry.camunda.cloud` is already integrated into this repo's CI per the precedent above — with (c) worth revisiting if/when AWS e2e coverage expands to services LocalStack's free tier doesn't emulate, since at that point a paid license may pay for itself by replacing what would otherwise be several bespoke emulator integrations under option (b). This is a recommendation, not a decision: it trades off cost, ownership, and coverage in ways that are a product/team call, not something this ADR can settle unilaterally.

## Consequences

### Positive

- The current risk (silent dependency on an EOL, possibly-soon-to-require-auth image tag) is now written down instead of implicit, so it can't be silently rediscovered as a CI outage.
- `DockerImages` gained a zero-cost-when-unused escape hatch (`CONNECTORS_TEST_IMAGE_REGISTRY`). Whichever option the team picks, adopting a mirror requires no further code changes to any test module that already uses `DockerImages.get(...)` — kafka, rabbitmq, jdbc, http-client, and every AWS e2e suite pick up the override transparently.
- The comparison of (a)/(b)/(c) is available for the team to act on without re-deriving the trade-offs from scratch.

### Negative

- The actual long-term-strategy decision remains open; this ADR does not reduce the underlying risk until a human picks an option and someone implements it (mirrors an image, adopts new emulators, or purchases a license — none of which this PR performs).
- The registry-override mechanism is unconditional prefixing (see Javadoc on `DockerImages.get`): an entry that already carries an explicit registry host would be nested under the override rather than having its host replaced. This is a documented, deliberate simplification, not a defect, but it means option (a) as implemented would produce paths like `<registry>/localstack/localstack:4.8`, which the mirror-side tooling needs to expect.
- The LocalStack EOL/licensing facts in this ADR are carried over from the GitHub issue, not independently re-verified against LocalStack's current terms — a follow-up should confirm them before the team commits to an option.
