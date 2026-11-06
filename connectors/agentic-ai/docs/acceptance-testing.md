# Acceptance testing through public interfaces

Test use cases and observable behavior, not per-class coverage gaps. Where practical, express the
expected behavior or reproduce a bug with a failing test before implementing the change.
Do not require non-compiling tests.

Accept a complete user-facing feature with an e2e test through customer-available interfaces.
Use CPT with BPMN, templates and process variables, or the exposed API. Assert customer-visible
results, including required observability. For integration-dependent behavior, follow the
[integration-target rules](#integration-targets).

## Choosing e2e vs unit tests (mandatory decision)

Choose the test level before implementing each code change; an e2e test is not required for every
change. If you add none for a change that touches the element template or behavioral characteristics,
say why. If requirements
are unclear, ask before starting. The examples below are illustrative, not exhaustive; reason from
the principle: does correctness depend on the engine and BPMN wiring?

- **E2E test required** when the change touches the distributed / BPMN contract:
    - job completion semantics or ad-hoc sub-process directives
    - element activation from tool calls
    - variable flow to/from Zeebe (`toolCallResults`, `agentContext`, `toolCall`, `toolCallResult`)
    - conversation memory persistence across job boundaries
    - event handling (interrupt / wait behaviors)
    - the full request → LLM → response cycle
    - element-template behavior
- **Tests without a cluster are sufficient** when correctness is provable without the engine:
    - prompt composition, schema generation, content/tool conversion
    - response formatting, error messages, pure transformations

## Test suites

Unit and component/integration tests that do not require a Camunda cluster belong in the connector
modules. Use JUnit 5, Mockito and AssertJ as appropriate; Spring context slice, autoconfiguration
and WireMock tests are also valid when they exercise the relevant behavior.

E2E tests live in `connectors-e2e-test/connectors-e2e-test-agentic-ai/` (Camunda Process Test scenarios
plus WireMock LLM stubs, and acceptance tests against real LLM providers). Its
[`AGENTS.md`](../../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md) describes the e2e
modules, their prerequisites, how to run them, where new tests go, and the real-LLM CPT coverage.
The [E2E runbook](../../../connectors-e2e-test/connectors-e2e-test-agentic-ai/README.md) covers test
ownership, local commands, paid-provider authorization, and how to verify that tests ran.

## Integration targets

For changes that depend on an integration, name the test target and explain what it establishes
in the PR description. A real protocol reference server can establish MCP/A2A interoperability
without LLM API keys. When behavior depends on an LLM provider, add a real-provider test if API
keys are available.

WireMock tests can establish connector behavior and BPMN wiring, but do not prove interoperability
with a real integration. When credentials or other prerequisites are unavailable, state in the PR
description what was tested and what remains unverified.
Do not report a stubbed run as a successful real-integration test.

## Real-provider coverage

Real-provider changes update the `RealProviderApiSmokeSupport` provider/capability matrix and
`RealProviderSelectionTest` where applicable. Update the corresponding `ci: true` registry row or record an
explicit manual-only decision in `requiredCiCapabilities` (see
[Real-LLM CPT coverage](../../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md#real-llm-cpt-coverage)).
