# AI Agent element templates

This directory contains the [element templates](https://docs.camunda.io/docs/components/modeler/desktop-modeler/element-templates/about-templates/)
of the AI Agent connectors. The templates of the other Agentic AI connectors live in their own modules,
see the [index of all Agentic AI element templates](../../connector-agentic-ai/element-templates/README.md).

## Which template do I need?

Templates are versioned per Camunda minor version. The tables below list the newest template for each
Camunda minor version that the connector supports. The newest template lives directly in this folder.
Templates for older Camunda minor versions live in [`versioned/`](./versioned/).

Pick the row with the highest minimum Camunda version that is not higher than the Camunda version you
run. A template also works on later Camunda minor versions: the `engines.camunda` field `^8.9` means
"Camunda 8.9 or later".

For example, on Camunda 8.9 use the AI Agent Task (v1) template version `7`. On Camunda 8.10 use the
AI Agent Task (v2) template version `1`. The AI Agent Task (v1) template version `13` also runs on
Camunda 8.10, but it is deprecated.

## AI Agent connectors

See the [AI Agent connector documentation](https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent/).
The AI Agent ships in two flavors that share the same versioning scheme.

### AI Agent Task

AI Agent Task (v2) is a new, independently versioned connector type (`io.camunda.agenticai:aiagent:task:2`)
running on the new chat-model provider SPI, not a new template version of AI Agent Task (v1). As of
Camunda 8.10, AI Agent Task (v1) is deprecated in favor of (v2).

| Connector                      | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| AI Agent Task (v2)             | 8.10 | 1  | [`agenticai-ai-agent-task.v2.json`](./agenticai-ai-agent-task.v2.json) |
| AI Agent Task (v1, deprecated) | 8.10 | 13 | [`agenticai-aiagent-outbound-connector.json`](./agenticai-aiagent-outbound-connector.json) |
| AI Agent Task (v1)             | 8.9  | 7  | [`versioned/agenticai-aiagent-outbound-connector-7.json`](./versioned/agenticai-aiagent-outbound-connector-7.json) |
| AI Agent Task (v1)             | 8.8  | 5  | [`versioned/agenticai-aiagent-outbound-connector-5.json`](./versioned/agenticai-aiagent-outbound-connector-5.json) |

### AI Agent Sub-process

AI Agent Sub-process (v2) is a new, independently versioned connector type
(`io.camunda.agenticai:aiagent:subprocess:2`) running on the new chat-model provider SPI, not a new
template version of AI Agent Sub-process (v1). As of Camunda 8.10, AI Agent Sub-process (v1) is
deprecated in favor of (v2).

| Connector                             | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| AI Agent Sub-process (v2)             | 8.10 | 1  | [`agenticai-ai-agent-subprocess.v2.json`](./agenticai-ai-agent-subprocess.v2.json) |
| AI Agent Sub-process (v1, deprecated) | 8.10 | 13 | [`agenticai-aiagent-job-worker.json`](./agenticai-aiagent-job-worker.json) |
| AI Agent Sub-process (v1)             | 8.9  | 7  | [`versioned/agenticai-aiagent-job-worker-7.json`](./versioned/agenticai-aiagent-job-worker-7.json) |
| AI Agent Sub-process (v1)             | 8.8  | 5  | [`versioned/agenticai-aiagent-job-worker-5.json`](./versioned/agenticai-aiagent-job-worker-5.json) |

### Provider selection in v2 templates

All four v2 templates share provider/backend steps. Each entry name starts with the element kind,
`AI Agent Task: ` in the task templates and `AI Agent Sub-process: ` in the sub-process templates, so the
Append element popup lists both kinds apart. The **OpenAI compatible** category includes
Ollama, LM Studio, and the Camunda-provided LLM. Ollama and LM Studio select Chat Completions without
authentication at `http://localhost:11434/v1` and `http://localhost:1234/v1`.

The **Camunda-provided LLM (SaaS only)** preset selects the OpenAI-compatible Chat Completions backend
with API-key authentication and the FEEL secret references
`=camunda.secrets.CAMUNDA_PROVIDED_LLM_API_ENDPOINT`,
`=camunda.secrets.CAMUNDA_PROVIDED_LLM_API_KEY`, and
`=camunda.secrets.CAMUNDA_PROVIDED_LLM_DEFAULT_MODEL`. See the
[Camunda-provided LLM documentation](https://docs.camunda.io/docs/8.10/components/agentic-orchestration/camunda-provided-llm/)
for availability and supported models.

Backend IDs and labels come from the generated dropdowns. Edit preset defaults in
`../bin/transform-ai-agent-provider-steps.groovy`, not the generated JSON.

## Markers added by the transform scripts

### Agent definition marker

All AI Agent Task and Sub-process templates (v1 and v2, all flavors, including hybrid) carry a
hidden `zeebe:agentDefinition` property so the engine detects a native agent at deploy time
(camunda/connectors#8176). `../bin/transform-ai-agent-task-template.groovy` adds it to each task template
(`agentType=aiAgentTask`); `../bin/transform-ai-agent-sub-process-template.groovy` adds its own
(`agentType=aiAgentSubProcess`) to the derived sub-process template. Both take `agentType` as a
Maven property. Both scripts drop any incoming `zeebe:agentDefinition` before adding their own, so
execution order does not matter. The sub-process transformer cannot carry the task marker into the
derived template regardless of when the task-marker script runs.

### Deprecation marker (v1 only)

Only the current main/hybrid v1 AI Agent Task and Sub-process templates (version 13, minimum Camunda
8.10) carry a top-level `deprecated` block pointing modelers to the v2 template, and have `(Deprecated)`
appended to their `name`. Their `versioned/` snapshots at versions 10 to 12 do not: Web Modeler never
offers an Update action on a deprecated template, so deprecating those older versions too would strand
anyone still on them with no click-path to version 13 (see #8431's follow-up revert). Earlier v1
versions (8.8/8.9-minimum, versions 0 to 7) carry neither, and neither do v2 templates. For the generated
main/hybrid templates, both transform scripts accept optional `deprecationMessage` /
`deprecationDocumentationRef` Maven properties (wired only into the v1 executions in `../pom.xml`), and
each script strips any inherited `deprecated` block before deciding its own and appending `(Deprecated)`
to `name` if not already present, the same pattern used for the agent definition marker above.
`versioned/` snapshots are static and are patched by hand instead (three pre-8.10 snapshots already
carried an unrelated legacy `"deprecated": true` boolean flag and were left untouched).

## Maintaining this index

This index must stay in sync with the JSON files in this folder every time a template version is
bumped. The newest template of each connector lives in this folder; superseded ones move into
[`versioned/`](./versioned/) when `versionHistoryEnabled` is set.
