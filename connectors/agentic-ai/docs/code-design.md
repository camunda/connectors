---
applyTo: "connectors/agentic-ai/**,connectors-e2e-test/connectors-e2e-test-agentic-ai/**"
---

# Agentic AI code design

Make new code agree with the [target architecture](target-architecture.md). Follow the rules when you plan a
change and when you write code. In a review, flag each change that breaks a rule. Give the name of the rule.

- **Rich domain models.** Put behavior on the type that owns the data. Add a service only for I/O, for
  orchestration across domain types, or for an SPI. Use `AgentConversation` as the example.
- **Tell, don't ask.** Do not read getters and make the decision outside the model. Call a domain
  method. The method returns the new state as a new instance. Example:
  `conversation.ingest(message, metrics)`.
- **No side effects on arguments.** A public or package-private method returns a new value. It does
  not change its arguments. A private method can add items to a local accumulator.
- **Sealed and exhaustive.** Make a closed hierarchy `sealed`. Match it with a pattern `switch` that has
  no `default` branch. Then a new subtype causes a compile error at each location that does not handle
  it. Do not write a chain of `instanceof` checks. Use records for values.
- **No new utility classes.** Put a function on the type that it is about, or on a named domain concept
  such as `DocumentMimeTypes`. Do not add `*Util` or `*Utils` classes.
- **`Optional` only as a return type.** Use `@Nullable` for fields and parameters.
- **One reason to change per class.** When an orchestrator gets a new responsibility, move it into a
  named step. Do not add one more method to the orchestrator.

For interfaces, follow the convention in [`AGENTS.md`](../AGENTS.md#architectural-invariants) (#9539).
