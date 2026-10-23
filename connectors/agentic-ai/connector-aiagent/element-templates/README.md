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

## Maintaining this index

This index must stay in sync with the JSON files in this folder every time a template version is
bumped. The newest template of each connector lives in this folder; superseded ones move into
[`versioned/`](./versioned/) when `versionHistoryEnabled` is set.
