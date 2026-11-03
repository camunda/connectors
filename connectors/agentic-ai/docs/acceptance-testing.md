# Acceptance testing through public interfaces

Follow TDD (red, green, refactor) wherever possible. E2e tests can be written implementation-independently
first: express the behavior, watch it fail, implement, then confirm it passes. Test use cases and
observable behavior, not per-class coverage gaps.

Accept a complete user-facing feature only with end-to-end evidence through customer-available
interfaces (CPT with BPMN, templates and process variables, or the exposed API) and real integrations
relevant to that feature. Assert customer-visible results, including required observability.
WireMock is fine for internal tests and incremental development; mock-based tests alone do not
satisfy feature acceptance criteria.

## Choosing e2e vs unit tests (mandatory decision)

For every code change, explicitly decide whether an e2e test is warranted, using this rule. If you add
none for a change that touches the element template or behavioral characteristics, say why. If you are unsure, ask before
starting a change. The examples below are illustrative, not exhaustive; when a case is not
listed, reason from the principle (does correctness depend on the engine and BPMN wiring?).

- **E2E test required** when the change touches the distributed / BPMN contract:
    - job completion semantics or ad-hoc sub-process directives
    - element activation from tool calls
    - variable flow to/from Zeebe (`toolCallResults`, `agentContext`, `toolCall`, `toolCallResult`)
    - conversation memory persistence across job boundaries
    - event handling (interrupt / wait behaviors)
    - the full request → LLM → response cycle
    - element-template behavior
- **Unit-test-only is sufficient** when the change is internal to a single class/converter and provable
  without the engine:
    - prompt composition, schema generation, content/tool conversion
    - response formatting, error messages, pure transformations

## Test suites

Unit tests use JUnit 5, Mockito, and AssertJ. In this module use unit tests only, apart from the few
existing Spring Boot tests and the e2e suite.

E2E tests live in `connectors-e2e-test/connectors-e2e-test-agentic-ai/` (Camunda Process Test scenarios
plus WireMock LLM stubs, and acceptance tests against real LLM providers). Its
[`AGENTS.md`](../../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md) describes the e2e
modules, their prerequisites, how to run them, where new tests go, and the real-LLM CPT coverage.
The [E2E runbook](../../../connectors-e2e-test/connectors-e2e-test-agentic-ai/README.md) covers test
ownership, local commands, paid-provider authorization, and how to verify that tests ran.

## Real-provider coverage

Real-provider changes update the `RealProviderApiSmokeSupport` provider/capability matrix and
`RealProviderSelectionTest` where applicable, plus the corresponding `ci: true` registry row or an
explicit manual-only decision in `requiredCiCapabilities` (see
[Real-LLM CPT coverage](../../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md#real-llm-cpt-coverage)).
