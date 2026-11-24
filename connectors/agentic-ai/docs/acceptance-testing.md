# Acceptance testing through public interfaces

Test use cases and observable behavior, not per-class coverage gaps. Where practical, express the
expected behavior or reproduce a bug with a failing test before implementing the change.
Do not require non-compiling tests.

Accept a complete user-facing capability with an e2e test through customer-available interfaces.
Use CPT with BPMN, templates and process variables, or the exposed API. Assert customer-visible
results, including required observability. For integration-dependent behavior, follow the
[integration-target rules](#integration-targets). Each test asserts the outcome it is about
([domain rule 6](implementation-rules.md#domain-rules)).

## Choosing e2e vs unit tests (mandatory decision)

Choose the test level before implementing each code change; an e2e test is not required for every
change. If you add none for a change that touches the element template or behavioral characteristics,
say why. In a stack of PRs, the reason can name the later PR that adds the e2e test. If requirements
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

When the change needs an e2e test, or adds or changes one, add the `e2e-tests` label to the PR. CI
then runs all e2e modules on each push. Without the label, the e2e tests run only nightly, after the
merge.

## Unit tests

These rules apply [P4](implementation-rules.md#principles): each test protects one behavior that a
plausible change can break. Pin the outcome the test is about ([domain rule 6](implementation-rules.md#domain-rules))
at the level of the unit that owns it. The test level decision above stays mandatory.

- **A test lives in the test of the unit that it exercises.** A test class tests the unit it is named
  after: a domain type, a step or an adapter. Put a test of a different type into the test of that
  type. A component test stays at its component level and tests use cases.
- **A test that only reads back its input has no purpose.** Do not assert that an accessor returns its
  constructor argument. Test derived values, validation and decisions.
- **Mock only I/O collaborators.** These are SPIs, stores, clients and the engine. Build records,
  domain types and steps for real, with their constructors or builders. Test a core decision without
  mocks. Example: `AgentConversationTest`. A build check can find a mock of a record or a domain type.
- **Test shared behavior once.** In unit and component tests, test the behavior of a base class in one
  place. A flavor test covers only what the flavor changes. A PR can delete a duplicate test when it
  names the test that keeps the coverage. E2e tests cover the AI Agent Task and the AI Agent
  Sub-process separately, because their BPMN models differ.
- **Assert outcomes. Verify only interactions with I/O collaborators.** Assert the returned value or state.
  Use `verify` and `inOrder` only for calls to an external system, where domain rule 6 needs the
  number of calls.

## Test suites

Unit and component/integration tests that do not require a Camunda cluster belong in the connector
modules. Use JUnit 5, Mockito and AssertJ as appropriate; Spring context slice, autoconfiguration
and WireMock tests are also valid when they exercise the relevant behavior.

E2E tests live in `connectors-e2e-test/connectors-e2e-test-agentic-ai/`: see its
[`AGENTS.md`](../../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md) and the
[E2E runbook](../../../connectors-e2e-test/connectors-e2e-test-agentic-ai/README.md).

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
