---
applyTo: "connectors/agentic-ai/**,connectors-e2e-test/connectors-e2e-test-agentic-ai/**"
---

# Agentic AI implementation rules

These rules apply to `connectors/agentic-ai` and `connectors-e2e-test/connectors-e2e-test-agentic-ai`.
Follow them when you plan a change and when you write code. In a review, flag each change that breaks
a rule. Give the name of the rule.

Follow the repo-wide connectors conventions in the repo-root [`AGENTS.md`](../../../AGENTS.md) where
they do not conflict with these rules. If they conflict, these rules apply, and the PR states the
divergence. Module operating rules (testing, the Definition of Done, ADRs, architectural invariants)
are in [`connectors/agentic-ai/AGENTS.md`](../AGENTS.md).

Review a rule when its SDK or API area changes. Remove a rule when a build check enforces it. Remove a
rule when it only causes noise. When a review finds a problem that a build check can find, add the
check, or open a follow-up issue under #9591.

## Before you plan

A change in this module often has more than one location to change. Before you plan, find each
location that the change must reach:

- **Both flavors:** the AI Agent Task and the AI Agent Sub-process.
- **All element template versions** that expose the feature: v1 and v2.
- **All conversation stores:** in-process, Camunda document and AWS AgentCore.
- **All providers** that the change applies to, native and LangChain4j.
- **Running process instances:** instances that started on an earlier connector version, and process
  instance migration. See [domain rule 1](#domain-rules).

The plan names each location, or says why the change does not apply to it.

## Simplicity first

Write the minimum code that solves the problem. Write nothing speculative.

- Add no feature that the task does not ask for.
- Add no flexibility or configuration that the task does not ask for.
- Add no error handling for a case that cannot occur.

## Surgical changes

Change only what the task needs. Clean up only your own changes.

- Do not improve adjacent code, comments or formatting.
- Do not refactor code that works. Ask before each refactor, also a small one.
- Tell about dead code that is not related to your change. Do not remove it unless you get a request.
- Remove the variables and functions that your change made unused.
- The test: each changed line comes directly from the request.

## Domain rules

Each rule gives a principle, the location in this module where it applies, and the review finding
that caused it.

1. **Changes are compatible with running processes.** A process that runs today MUST continue to
   deploy and run after the change. A new field or capability MUST NOT change behavior when nobody
   sets it.
   - In this module: a new element template field is optional. It has no default value, or a default
     value that keeps the current behavior. This also applies to a field in a new optional object.
     (#5904, #7765)
   - Call an SDK overload that adds a field only when the field has a value.
   - A changed default value in a template is a behavior change for users who deploy with that
     template. Deployed process instances keep the value they have. Keep the old default in the
     template versions whose users expect it, and state the change in the PR. Example: v1 keeps the
     old default system prompt through a per-template override. (#9036)
   - Do not deprecate a template version that users must update from. Web Modeler shows no Update
     action for a deprecated template. (#8896)
   - Read the state that an earlier connector version stored. Example: messages without an ID load,
     and `AgentContextSchemaMigration` migrates older stored agent contexts. (#8318)
   - Keep the agent state through a process instance migration. Example: the agent kept its
     `agentInstanceKey` only after the fix in #9164.
   - Why: a running process instance continues on the new connector version without a redeployment.
2. **Check the source before you use vendor-specific data.** Data is valid only in the system that
   made it. An ID keeps its meaning.
   - In this module: when you write content that only one provider can use, tag it with that provider.
     Examples: `ReasoningContent` and `ProviderContent` have a `provider` field.
   - A provider converter MUST compare the tag, and MUST drop reasoning, provider-native content and
     IDs from a different provider. Use `AnthropicContentConverter` as the example.
   - Map a protocol ID only to the field with the same meaning. Example: use the output item ID, not
     the response ID.
   - Why: a conversation can change to a different provider. The target provider rejects data from a
     different provider or data in the wrong field. (#8343, #8272, #8279)
3. **Parse structured values. Do not join them as strings.**
   - Parse the URL and build the result with the URI API. Do not use string joins or suffix checks on
     the URL text.
   - In this module: normalize custom endpoint URLs this way.
   - Write a test for each case: trailing slashes, an existing suffix, a query and a fragment.
   - Why: string joins made incorrect paths. (#8393)
4. **Each implementation of an extension point fulfills the full contract, or fails with a clear
   error.**
   - In this module: a new provider sends the configured response format (JSON or JSON schema) to the
     model.
   - A new provider converts each content block: text, image and document.
   - If the provider does not support a part of the contract, it throws an error that names the
     unsupported value. A test covers the error. Examples: `GeminiContentConverter` for an unsupported
     document type, `BedrockConverseRequestConverter` for unsupported content in a system message.
   - The help text of a new provider shows the value format that the runtime accepts.
   - Why: a missing part fails at runtime without an error. (#7129)
5. **Claim only the guarantees that the other system enforces.**
   - In this module: send the job key and the job lease token on every agent instance command. The
     engine rejects a command without them.
   - The engine rejects a stale lease only on create. On update, it accepts the command and skips the
     status change.
   - Do not document fencing that the engine does not do.
   - Why: older docs claimed fencing that the engine did not do. (#8342)
6. **Tests pin the whole observable outcome.** An assertion covers the complete result and the number
   of interactions, not a subset. This rule applies to all test code, including the e2e module.
   - Compare complete payloads, schemas and messages. Do not assert only some of their fields.
   - Prefer equality assertions (`isEqualTo`, `containsExactly`) to `contains`.
   - Compare JSON with `JSONAssert` in strict mode, on the full object. Do not use `contains` on JSON
     text. Example: `CamundaDocumentConversationStoreTest`.
   - When code calls an external system in a loop or a retry, assert the exact number of calls.
   - In a review, flag a change that removes or weakens an assertion, unless the behavior changed on
     purpose and the PR says so.
   - In this module: compare the full input schema of a tool definition. Assert the number of model
     requests in an agentic loop test, for example `modelCallCount()`.
   - Why: a partial assertion lets a regression pass. A schema regression and an extra loop iteration
     passed this way. (#7463, #6999)

## Code design

Make new code agree with the [target architecture](target-architecture.md) (`connectors/agentic-ai/docs/target-architecture.md`). These rules are not from
past findings. They apply to new code. Existing code moves to them only in dedicated issues.

Write code in a functional style. A function gets its inputs as arguments and gives its result as the
return value. Values are immutable. A sealed type shows each possible outcome, so the caller must
handle each one. Side effects (I/O, Spring, the engine) stay in the services at the edge. The rules
below make this style specific.

- **Rich domain models.** Put behavior on the type that owns the data. Example: `AgentConversation`.
  A service is a class without domain state: a handler, composer, converter, registry, store or
  client. Add a service only for I/O, for orchestration across domain types, or for an SPI.
- **Tell, don't ask.** Do not read getters and make the decision outside the model. Call a domain
  method. The method returns the new state as a new instance. Example:
  `conversation.ingest(message, metrics)`.
- **Immutable by default.** Use records for values. When a record does not fit, use a final class
  without side effects. Example: the canonical constructor is too large, and the type needs named
  factory methods such as `of`, `fromFoo` or `fromBar`. A change makes a new instance.
- **No side effects on arguments.** A public or package-private method returns a new value. It does
  not change its arguments. A private method can add items to a local accumulator. Example: do not
  write `void addToolCalls(List<ToolCall> target, ...)` that adds items to the list from the caller.
  Write `List<ToolCall> toolCalls(...)` that returns a new list.
- **Sealed and exhaustive.** Make a closed hierarchy `sealed`. Match it with a pattern `switch` that has
  no `default` branch. Then a new subtype causes a compile error at each location that does not handle
  it. Do not write a chain of `instanceof` checks. Example: `AgentInitializationResult` and its
  `switch` in `BaseAgentRequestHandler.handleRequest`.
- **Outcomes as types.** When a step has more than one expected outcome, return a sealed outcome type.
  Do not use a flag, `null` or an exception for an expected outcome. Use an exception only for an
  error. Example: `CompositionResult` with `NextTurn`, `NoInput` and `Deferred`.
- **Null-marked code.** All code is `@NullMarked`. Mark each value that can be absent with `@Nullable`.
  See [Null safety in `connectors/agentic-ai/AGENTS.md`](../AGENTS.md#null-safety).
- **`Optional` only as a return type.** Use `@Nullable` for fields and parameters.
- **Generated builders, not long constructors.** For a record with many components, use
  `@AgenticAiRecord` and its generated builder. Example: `AgentMetrics`.
- **No new utility classes.** Put a function on the type that it is about, or on a named domain concept.
  Example: `DocumentMimeTypes`. Do not add `*Util` or `*Utils` classes.
- **Wire the main collaborators with dependency injection.** Make each service a Spring bean with
  `@ConditionalOnMissingBean`, also when it has no interface. A user can then replace it with a bean
  of their own. Example: `aiAgentInstanceToolMapper` for `AgentInstanceToolMapper`. Create a small
  helper with `new` inside its owner. When a bean is a good candidate for a public interface (for
  example an SPI that users can implement), say so in the plan or the PR. The team decides about the
  interface.
- **One reason to change per class.** When an orchestrator gets a new responsibility, move it into a
  named step. Do not add one more method to the orchestrator. Example: `BaseAgentRequestHandler` calls
  `AgentInitializer` and `AgentConversationTurnInputComposer` as steps.

For interfaces, follow the convention in [`connectors/agentic-ai/AGENTS.md`](../AGENTS.md#architectural-invariants) (#9539).
