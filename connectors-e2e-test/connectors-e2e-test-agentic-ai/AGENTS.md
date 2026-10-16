# Agentic AI E2E Tests: Agent Instructions

The e2e tests of the agentic AI connectors: Camunda Process Test scenarios with WireMock stubs for the
LLM providers, MCP servers and A2A agents, plus acceptance tests against real LLM providers.

This file covers how the e2e modules are structured, how to run them and where new tests go. When a
change in `connectors/agentic-ai` needs an e2e test, and what a change must update before it is done,
is decided by the rules in the [agentic AI `AGENTS.md`](../../connectors/agentic-ai/AGENTS.md):
the [e2e vs unit decision](../../connectors/agentic-ai/AGENTS.md#choosing-e2e-vs-unit-tests-mandatory-decision)
and the [Definition of Done](../../connectors/agentic-ai/AGENTS.md#definition-of-done). For repo-wide
build, commit, PR and CI rules, see the repo-root [`AGENTS.md`](../../AGENTS.md).

See the [E2E runbook](README.md) for test ownership, local commands, paid-provider authorization, and
how to verify that tests ran.

## Modules

`connectors-e2e-test-agentic-ai` is an aggregator. Each e2e module depends only on the connector modules
it tests, so it runs a runtime with only these connectors:

| E2E module                                    | Tests                                                                                                 |
|-----------------------------------------------|-------------------------------------------------------------------------------------------------------|
| `connectors-e2e-test-agentic-ai-base`         | None. Shared test support in `src/main` (`BaseAgenticAiTest`, `BpmnUtil`, `TestUtil`, document store) |
| `connectors-e2e-test-agentic-ai-aiagent-base` | None. Shared AI Agent test support in `src/main` (`AgentTestFixtures`, the `assertj` package)         |
| `connectors-e2e-test-agentic-ai-aiagent`      | AI Agent (both flavors), ad-hoc tools schema, MCP and A2A gateway tools                               |
| `connectors-e2e-test-agentic-ai-real-llm`     | AI Agent against real LLM providers (CPT), with the AI Agent connector only                           |
| `connectors-e2e-test-agentic-ai-mcp`          | MCP client connectors, including the authentication tests                                             |
| `connectors-e2e-test-agentic-ai-a2a`          | A2A client connectors                                                                                 |

- Keep the base module free of agentic AI connector dependencies, and the AI Agent base module free of
  other agentic AI connectors. Otherwise the MCP, A2A or real LLM modules start connectors they do not
  test.
- Shared test support goes to `src/main` of a `*-base` module. The CI test matrix skips modules whose
  name ends with `-base`.
- Do not depend on another e2e module's test classes (`test-jar`). Move the shared code to a `*-base`
  module instead.
- A new e2e module needs no CI change when its name follows `connectors-e2e-test-agentic-ai-<name>`:
  the E2E workflow discovers it and runs it as its own job. Only the AI Agent module has a runner
  override in the E2E workflows.

## Prerequisites

The e2e tests require `element-templates-cli` on your PATH. The e2e harness shells out to it
(`BpmnFile.apply`) to apply templates, so it must be installed even though no pom declares it. When it
is missing, e2e tests fail with errors that can look unrelated to your change. Follow the pinned-version
prerequisites in the [E2E runbook](README.md).

The tests start containers (Camunda, Keycloak, the MCP test server), so Docker must be running.

## Running the tests

Run one e2e module, or one test class of it:

```bash
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai/connectors-e2e-test-agentic-ai-aiagent -Dtest=<TestClassName>
```

- Point `-pl` at the e2e module, not at `connectors-e2e-test-agentic-ai`: for the aggregator path,
  Maven builds only the aggregator and runs no tests.
- Without `-am`, Maven takes the connector modules and the `*-base` modules from the local Maven
  repository. Install your changes first (`mvn install -DskipTests -f connectors/agentic-ai/pom.xml`),
  or add the `*-base` modules to `-pl`.
- Running the full e2e suite is slow. Search the e2e modules for tests relevant to your change and run
  those selectively. Ensure that all e2e tests pass after completing a major work item.

## Writing tests

- For AI Agent tests, extend `BaseAgentSubProcessTest` (sub-process flavor) or `BaseAgentTaskTest`
  (task flavor). Provider-specific tests extend the provider base classes below
  `aiagent.subprocess.provider` and `aiagent.task.provider`.
- The WireMock stubs of the LLM providers live in the `aiagent.wiremock` package. WireMock serves
  `__files` and `mappings` from `src/test/resources` of the module that runs the test, so put test
  files into the module that uses them.
- Paths to element templates and other files of `connectors/agentic-ai` are relative to the module
  directory (`../../../connectors/...`). `AgentTestFixtures` holds the template paths.
- Keep all Java packages below `io.camunda.connector.e2e.agenticai`, independent of the module.

### Element template regression fixtures

When the `engines.camunda` minimum of the AI Agent templates is bumped, add regression fixtures for
the new template generation in the same change. The fixtures live in
`connectors-e2e-test-agentic-ai-aiagent/src/test/resources/regression/` (one Task and one Sub-process
BPMN per generation, e.g. `ai-agent-task-8.10.0.bpmn`) and are run by
`AgentTaskElementTemplateRegressionTests` and `AgentSubProcessElementTemplateRegressionTests`. Copy the
latest pair, rename it for the new version with unique process IDs (e.g. `AI_Agent_Task_8100`),
re-apply the new templates in Modeler, and add both files to the tests' `@ValueSource`. Never edit an
existing fixture.

## Real-LLM CPT coverage

Real-provider / real-LLM acceptance coverage lives in `connectors-e2e-test-agentic-ai-real-llm` and is
intentionally split in two dimensions:

1. **Provider shards**: `.github/workflows/AI_AGENT_CPT_PR.yml` runs the pre-merge suite when a
   maintainer applies the `ai-agent-model-e2e-test` label. The registry in
   `connectors-e2e-test-agentic-ai-real-llm/src/test/resources/ai-agent-cpt/registry.json`
   defines the bundle test leg and the native-provider
   capability-group legs. The workflow validates that registry and uses its generated matrix.
   Credential profile names are selected by the registry, while their Vault and environment wiring
   remains explicit in the workflow. When adding or changing a provider capability, update the
   matching `ci: true` row or document why it is omitted from `requiredCiCapabilities` and remains
   manual-only. Add the workflow credential wiring as well when the row needs a new credential
   profile.

   Applying the label authorizes one run for the PR's current head SHA. After every push or other
   head-SHA change, remove and reapply the label to authorize a run for the new SHA. Fork PRs do not
   receive Vault-backed credentials. The workflow first classifies changes using the curated impact
   map, including shared dependencies and test infrastructure. Classification does not authorize
   paid execution. See the runbook for the three reported states. Matrix execution is capped at
   four concurrent legs.
2. **Provider capabilities**: `RealProviderApiSmokeSupport.ProviderConfig` declares each provider/model row
   together with the capabilities it supports (`STRUCTURED_OUTPUT`, `REASONING`, `PROMPT_CACHING`,
   `MULTIMODAL_USER_MESSAGE`). Capability scenarios must read this matrix instead of hard-coding
   provider labels. If a scenario needs provider-specific element-template properties, put them in the
   row's `capabilityProperties` entry for that capability.

   Capability-focused scenarios live in the `RealProvider*E2ETestIT` classes and use JUnit tags
   matching the registry's `groups` values.

   Keep structured JSON generation separate from structured output combined with tool
   orchestration. Gemini 3.8 supports the former, but can repeatedly call an already-completed tool
   when both tools and a response schema are present. Its structured-output row therefore runs the
   AI Agent Task v2 no-tool fixture while providers with `STRUCTURED_OUTPUT_WITH_TOOLS` run the
   single- and multi-tool subprocess scenarios.

   The always-on provider scenarios (`toolCallLoopSurfacesPlantedFact` and
   `userFeedbackLoopReplaysAssistantTextOnFollowUp`) use `providers()` directly and are not gated by a
   capability. When debugging one capability, run the matching method instead of the whole class.

The real LLM tests run only with the `it-real-llm` profile (Failsafe). Without it, the module runs only
the unit tests of the CPT harness, so the E2E workflows make no paid calls.

Use `REAL_LLM_PROVIDER_GROUP` (`openai`, `vertex`, `bedrock`, `anthropic`, `mistral`, or `local`) to
run only one provider group locally or in CI. Keep `RealProviderSelectionTest` updated when changing
shard-only behavior, disabled rows, or provider group membership.

Use the runbook's host command with strict provider selection and an explicitly chosen provider group.

Use the corresponding group name (`structured-output`, `reasoning`, or `multimodal-documents`) to
run another capability suite. Bedrock prompt caching runs in its provider shard. OpenAI and Gemini
prompt caching remain manual because their cache placement is opportunistic: unset
`REAL_LLM_PROVIDER_GROUP`, provide only the credentials for the provider under test, and use
`-Dgroups=prompt-caching`. CI selects the bundle-backed `AiAgentE2ETestIT` by class with
`-Dit.test`; native capability legs, including `DocumentToolCallResultsIT`, are selected by their
JUnit groups.

To select the structured-output class instead, replace `-Dgroups=core-smoke` in that command with
`-Dit.test=RealProviderStructuredOutputE2ETestIT`.

For JetBrains IDEs, create a JUnit run configuration for the capability-specific `*E2ETestIT` class.
Set the same environment variables in **Run configuration → Environment variables**, keep the working
directory at the repository root, and use the `connectors-e2e-test-agentic-ai-real-llm` module classpath. Check
the test output for skipped parameterized invocations before treating a green IDE run as full provider
coverage.

`DocumentToolCallResultsIT` is explicitly included in the `it-real-llm` profile because it does not
match the `*E2ETestIT` class pattern. Its active scenario deterministically checks the provider's
final response for the facts contained in every nested document; it does not require a separate judge
model. Add user-message document scenarios to `RealProviderMultimodalE2ETestIT`; add tool-result
document scenarios to `DocumentToolCallResultsIT`, which owns compatibility coverage across legacy
v1 and native v2 provider paths. Run it locally with `-Dgroups=document-tool-results` and set
whichever provider credentials you want its rows to exercise. Always set
`REAL_LLM_PROVIDER_GROUP` to the intended provider group; otherwise, every row whose credentials are
available can run. Use the runbook's host command with `-Dgroups=document-tool-results` to select
only the OpenAI document rows.

> `CLAUDE.md` in this directory imports this file via `@AGENTS.md`, giving a single source of truth.
> Edit `AGENTS.md`; never add content directly to `CLAUDE.md`.
