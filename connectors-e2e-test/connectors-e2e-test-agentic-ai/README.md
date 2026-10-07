# Agentic AI end-to-end tests

## Test ownership

- **Deterministic CPT**: the regular `*Tests` under
  [`src/test/java/io/camunda/connector/e2e/agenticai/`](src/test/java/io/camunda/connector/e2e/agenticai/)
  use test chat models or WireMock instead of paid provider APIs and run in Maven's `test` phase.
  Deterministic refers to provider behavior; process-based tests still require a healthy Camunda
  Process Test container runtime. For a lightweight credential-free catalog and selection check:

  ```bash
  ./mvnw test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai \
    -Dtest=RealProviderSelectionTest
  ```

- **Native real-provider CPT**:
  [`RealProvider*E2ETestIT`](src/test/java/io/camunda/connector/e2e/agenticai/e2e/) runs the connector
  in process against real provider APIs, grouped by capability.
- **Document CPT**:
  [`RealProviderMultimodalE2ETestIT.java`](src/test/java/io/camunda/connector/e2e/agenticai/e2e/RealProviderMultimodalE2ETestIT.java)
  owns user-message documents, while
  [`DocumentToolCallResultsIT.java`](src/test/java/io/camunda/connector/e2e/agenticai/e2e/DocumentToolCallResultsIT.java)
  owns tool-result documents across legacy v1 and native v2 providers.
- **Bundle E2E**:
  [`AiAgentE2ETestIT.java`](src/test/java/io/camunda/connector/e2e/agenticai/e2e/AiAgentE2ETestIT.java)
  is the separate image-backed suite that verifies the packaged connectors bundle.

## Sources of truth

- Native provider/model rows, required environment variables, and capabilities:
  [`RealProviderApiSmokeSupport.java`](src/test/java/io/camunda/connector/e2e/agenticai/e2e/RealProviderApiSmokeSupport.java).
- Document provider/model rows:
  [`DocumentToolCallResultsIT.java`](src/test/java/io/camunda/connector/e2e/agenticai/e2e/DocumentToolCallResultsIT.java).
- Capability group names:
  [`RealProviderCapabilityTags.java`](src/test/java/io/camunda/connector/e2e/agenticai/e2e/RealProviderCapabilityTags.java).
- CI execution rows and required capability coverage:
  [`src/test/resources/ai-agent-cpt/registry.json`](src/test/resources/ai-agent-cpt/registry.json),
  consumed by
  [`AI_AGENT_CPT_PR.yml`](../../.github/workflows/AI_AGENT_CPT_PR.yml).

## Run one native capability from the host

The PR workflow is opt-in through the `ai-agent-model-e2e-test` label. Applying it authorizes the
current PR head; reapply it after the head changes. These tests call paid APIs and can incur cost, so
run them or apply the label only intentionally.

For the canonical host run, start at the repository root with JDK 21, Node.js 24, a
Docker-compatible runtime, and `element-templates-cli` on `PATH` at the version pinned in
[`package.json`](../../.github/workflows/package.json). Provide the credentials required by the
selected Java catalog row. This example directly selects the OpenAI core capability:

```bash
RUN_NATIVE_LLM_E2E=true \
REQUIRE_NATIVE_LLM_PROVIDER=true \
REAL_LLM_PROVIDER_GROUP=openai \
OPENAI_API_KEY='<your-key>' \
./mvnw verify -pl connectors-e2e-test/connectors-e2e-test-agentic-ai \
  -Pit-real-llm -Dgroups=core-smoke
```

Keep `REQUIRE_NATIVE_LLM_PROVIDER=true`: it turns missing credentials, an unsupported
provider/capability selection, or zero enabled provider rows into a failure. Without it, the
parameterized tests allow zero invocations; without `RUN_NATIVE_LLM_E2E=true`, the real-provider
classes are skipped.

Do not treat `BUILD SUCCESS` alone as proof of coverage. Check the parameterized provider labels in
the Maven output and the positive `Tests run: N` (`N > 0`) summary with no failures or errors in
`connectors-e2e-test/connectors-e2e-test-agentic-ai/target/failsafe-reports`. Skipped classes or a
zero-test summary mean the selected real-provider coverage did not execute.
