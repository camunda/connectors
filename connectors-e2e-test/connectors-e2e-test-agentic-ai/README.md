# Agentic AI end-to-end tests

Run the smallest test that covers your change. Real-provider tests make paid API calls; do not run
them or apply the opt-in label without authorization.

## Test ownership

- **Deterministic Camunda Process Tests (CPT)**: the regular `*Tests` in the
  [AI Agent](connectors-e2e-test-agentic-ai-aiagent/src/test/java/io/camunda/connector/e2e/agenticai/),
  [MCP](connectors-e2e-test-agentic-ai-mcp/src/test/java/io/camunda/connector/e2e/agenticai/mcp/) and
  [A2A](connectors-e2e-test-agentic-ai-a2a/src/test/java/io/camunda/connector/e2e/agenticai/a2a/) e2e modules
  use test chat models or WireMock instead of paid provider APIs. Process tests still need Docker.
  [`AGENTS.md`](AGENTS.md) lists all e2e modules.
- **Native real-provider CPT**: the
  [`RealProvider*E2ETestIT` classes](connectors-e2e-test-agentic-ai-real-llm/src/test/java/io/camunda/connector/e2e/agenticai/realllm/)
  run the connector in process against real provider APIs, grouped by capability.
- **Document CPT**:
  [`RealProviderMultimodalE2ETestIT.java`](connectors-e2e-test-agentic-ai-real-llm/src/test/java/io/camunda/connector/e2e/agenticai/realllm/RealProviderMultimodalE2ETestIT.java)
  owns user-message documents.
  [`DocumentToolCallResultsIT.java`](connectors-e2e-test-agentic-ai-real-llm/src/test/java/io/camunda/connector/e2e/agenticai/realllm/DocumentToolCallResultsIT.java)
  owns tool-result documents across legacy v1 and native v2 providers.
- **Bundle end-to-end tests**:
  [`AiAgentE2ETestIT.java`](connectors-e2e-test-agentic-ai-real-llm/src/test/java/io/camunda/connector/e2e/agenticai/realllm/AiAgentE2ETestIT.java)
  tests the packaged connectors bundle in a separate container.

## Sources of truth

- Provider/model rows, required environment variables, and capabilities:
  [`RealProviderApiSmokeSupport.java`](connectors-e2e-test-agentic-ai-real-llm/src/test/java/io/camunda/connector/e2e/agenticai/realllm/RealProviderApiSmokeSupport.java).
- Document provider/model rows:
  [`DocumentToolCallResultsIT.java`](connectors-e2e-test-agentic-ai-real-llm/src/test/java/io/camunda/connector/e2e/agenticai/realllm/DocumentToolCallResultsIT.java).
- Capability group names:
  [`RealProviderCapabilityTags.java`](connectors-e2e-test-agentic-ai-real-llm/src/test/java/io/camunda/connector/e2e/agenticai/realllm/RealProviderCapabilityTags.java).
- Continuous integration (CI) execution rows and required capability coverage:
  [`registry.json`](connectors-e2e-test-agentic-ai-real-llm/src/test/resources/ai-agent-cpt/registry.json).
- Curated change-selection rules:
  [`impact-map.json`](../../.github/ai-agent-cpt/impact-map.json).

When changing dependency or build inputs relevant to CPT, check the impact map. Update its paths and
add positive and negative cases to
[`test_classify_impact.py`](../../.github/ai-agent-cpt/test_classify_impact.py).
The map is not a complete dependency graph. Change the registry for CI coverage changes and the
workflow for new credential wiring.

## PR selection and authorization

[`AI_AGENT_CPT_PR.yml`](../../.github/workflows/AI_AGENT_CPT_PR.yml) classifies same-repository pull
requests on opening, reopening, pushing, and labeling. Its summary distinguishes:

| State | Meaning |
|---|---|
| Not affected | No curated path matched. Paid jobs do not run. |
| Affected, waiting for authorization | Relevant paths changed, but this event does not authorize paid jobs. |
| Affected and authorized | Applying `ai-agent-model-e2e-test` authorizes paid jobs for that event's head commit. |

Review the head before applying the label: the run gets live-provider credentials. The label does not
protect against malicious same-repository workflow changes. Fork pull requests do not run these jobs.

After a push, remove and reapply the label. Leaving it attached does not authorize later commits.
Check the run's head commit and each matrix result. A green classification job with skipped provider
jobs is not a passing provider run.

The pull request workflow downloads Maven through the authenticated Nexus proxy and caches its
distribution separately from Maven dependencies. It saves the distribution immediately after
bootstrap, even if later tests fail. The proxy URL stays set for every wrapper invocation so later
steps reuse the same distribution; proxy credentials are available only during bootstrap. A cache
hit avoids downloading Maven, while a cache miss uses Nexus instead of Maven Central directly.

## Run one native capability from the host

Start at the repository root with JDK 21, Node.js 24, a working Docker-compatible runtime, and
`element-templates-cli` on `PATH` at the version pinned in
[`package.json`](../../.github/workflows/package.json). Use Maven settings with access to the
repository's required artifacts. If reactor dependencies are not installed locally, build them
without running tests:

```bash
./mvnw install -pl connectors-e2e-test/connectors-e2e-test-agentic-ai/connectors-e2e-test-agentic-ai-real-llm \
  -am -DskipTests -DskipChecks
```

Load `OPENAI_API_KEY` through your authorized local secret-access method, not the CI AppRole or shell
history. This command runs the OpenAI group's core capability against paid APIs:

```bash
RUN_NATIVE_LLM_E2E=true \
REQUIRE_NATIVE_LLM_PROVIDER=true \
REAL_LLM_PROVIDER_GROUP=openai \
./mvnw verify -pl connectors-e2e-test/connectors-e2e-test-agentic-ai/connectors-e2e-test-agentic-ai-real-llm \
  -Pit-real-llm -Dgroups=core-smoke
```

Keep both `true` flags: `RUN_NATIVE_LLM_E2E` enables live classes; `REQUIRE_NATIVE_LLM_PROVIDER`
fails on an empty provider/capability selection. It does not require every catalog row.
Without a provider group, all eligible rows with available credentials can run.

Check provider labels in the output and this run's reports under
`connectors-e2e-test/connectors-e2e-test-agentic-ai/connectors-e2e-test-agentic-ai-real-llm/target/failsafe-reports`; do not count old reports.
Require positive test counts with no failures, errors, or unexpected skips. `BUILD SUCCESS` alone
does not prove live coverage.

For a credential-free provider-selection check that does not call paid APIs:

```bash
./mvnw test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai/connectors-e2e-test-agentic-ai-real-llm \
  -Dtest=RealProviderSelectionTest
```
