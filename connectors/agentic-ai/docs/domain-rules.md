---
applyTo: "connectors/agentic-ai/**,connectors-e2e-test/connectors-e2e-test-agentic-ai/**"
---

# Agentic AI domain rules

Each rule gives a principle, the location in this module where it applies, and the review finding that caused it. Follow the rules when you change code. In a review, flag each change that breaks a rule. Give the name of the rule.

Review a rule when its SDK or API area changes. Remove a rule when a build check enforces it. Remove a rule when it only causes noise.

1. **Additive changes do nothing until used.** A new field or capability MUST NOT change behavior when nobody sets it.
   - In this module: a new element template field is nullable and not required. This also applies to a field in a new optional object.
   - A new element template field has no default value that changes behavior.
   - Call an SDK overload that adds a field only when the field has a value.
   - Why: existing processes must deploy and run without changes. (#5904, #7765)
2. **Check the source before you use vendor-specific data.** Data is valid only in the system that made it. An ID keeps its meaning.
   - In this module: a provider converter MUST drop reasoning, provider-native content and IDs from a different provider. Use `AnthropicContentConverter` as the example.
   - Map a protocol ID only to the field with the same meaning. Example: use the output item ID, not the response ID.
   - Why: a conversation can change to a different provider. The target provider rejects data from a different provider or data in the wrong field. (#8343, #8272, #8279)
3. **Parse structured values. Do not join them as strings.**
   - Parse the URL and build the result with the URI API. Do not use string joins or suffix checks on the URL text.
   - In this module: normalize custom endpoint URLs this way.
   - Write a test for each case: trailing slashes, an existing suffix, a query and a fragment.
   - Why: string joins made incorrect paths. (#8393)
4. **Each implementation of an extension point fulfills the full contract.**
   - In this module: a new provider sends the configured response format (JSON or JSON schema) to the model.
   - A new provider converts each content block: text, image and document.
   - The help text of a new provider shows the value format that the runtime accepts.
   - Why: a missing part fails at runtime without an error. (#7129)
5. **Claim only the guarantees that the other system enforces.**
   - In this module: send the job key and the job lease token on every agent instance command. The engine rejects a command without them.
   - The engine rejects a stale lease only on create. On update, it accepts the command and skips the status change.
   - Do not document fencing that the engine does not do.
   - Why: older docs claimed fencing that the engine did not do. (#8342)
6. **Tests pin the whole observable outcome.** An assertion covers the complete result and the number of interactions, not a subset. This rule applies to all test code, including the e2e module.
   - Compare complete payloads, schemas and messages. Do not assert only some of their fields.
   - When code calls an external system in a loop or a retry, assert the exact number of calls.
   - In a review, flag a change that removes or weakens an assertion, unless the behavior changed on purpose and the PR says so.
   - In this module: compare the full input schema of a tool definition. Assert the number of model requests in an agentic loop test, for example `modelCallCount()`.
   - Why: a partial assertion lets a regression pass. A schema regression and an extra loop iteration passed this way. (#7463, #6999)
