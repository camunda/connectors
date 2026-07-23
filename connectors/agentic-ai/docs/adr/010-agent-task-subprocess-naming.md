# Agent Task / Sub-process class naming

* Deciders: Agentic AI Team
* Date: July 23, 2026

## Status

**Implemented**

## Context and Problem Statement

The two AI Agent connector flavors' internal class names had drifted from the "AI Agent Task" /
"AI Agent Sub-process" flavor names already used in element templates and docs: production classes
were still called `AiAgentFunction`/`AiAgentJobWorker`, and request/response types carried a
`JobWorker`/`OutboundConnector` prefix inherited from the connector SDK concepts they wrap
(`OutboundConnectorAgentRequest`, `JobWorkerAgentResponse`, ...). We are about to introduce v2
connector types that need their own, clearly-versioned siblings alongside the existing ones, so the
existing classes need a `V1` marker to disambiguate them - and that marker needs to land in a
dedicated, prep-only change rather than mixed into the v2 introduction itself.

## Decision Outcome

Renamed across the module, in preparation for v2, before any v2 code is introduced:

- Drop the "Ai" prefix, settle on "Agent" (`AiAgentFunction` -> `AgentTaskV1Function`).
- Drop "JobWorker"/"OutboundConnector" in favor of "AgentSubProcess"/"AgentTask", matching the
  flavor names used in element templates and docs (`AiAgentJobWorker` -> `AgentSubProcessV1Function`,
  `OutboundConnectorAgentRequestHandler` -> `AgentTaskRequestHandler`).
- Introduce the "V1" suffix only on the top-level Function and Request classes, where the version
  will become part of the public contract once v2 siblings exist; everywhere else (execution
  contexts, request handlers, responses, provider configuration) has no per-version variance, so no
  suffix is added there.
- Move the legacy sealed provider configuration into its own `v1` package
  (`model/request/v1`), freeing up the simple name `ProviderConfiguration` for the wire-format-first
  v2 model to be introduced later.
- Extract the request-scoped fields shared between the two connector flavors' request records into
  standalone `AgentTaskRequestData` / `AgentSubProcessRequestData` classes, so a future v2 request
  type can reuse the same data shape instead of duplicating it.

None of this touches the public wire format: connector type IDs, Zeebe job variable names,
`@TemplateProperty` bindings and generated element-template JSON are unaffected -- this is an
internal class-naming and package-structure change only.

## Out of Scope

- Existing ADRs are not retroactively edited to use the new names; they remain a historical record
  of the class names in place at the time each decision was made.
