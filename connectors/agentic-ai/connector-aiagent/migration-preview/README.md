# AI Agent template migration preview

The generated v2 templates embed prototype recipes under `metadata.migratesFrom`.
This playground overlays the draft [bpmn-io/bpmn-js-element-templates#322](https://github.com/bpmn-io/bpmn-js-element-templates/pull/322)
top-level `migratesFrom` on copies of the four current v2 templates. It does not update
published templates, change connector runtime behavior, or migrate running instances.
Do not install these copies in a production Modeler or deploy the sample diagram.
Tracked in [#10548](https://github.com/camunda/connectors/issues/10548).

## Open the preview

From the Connectors root, with Python 3.9+ and Node.js 22+:

```sh
PREVIEW="$(mktemp -d)"
git clone https://github.com/bpmn-io/bpmn-js-element-templates.git "$PREVIEW"
git -C "$PREVIEW" fetch origin pull/322/head
git -C "$PREVIEW" checkout --detach e0d17a4869eedc6472ea56e56bc6dfea07bec23c
python3 connectors/agentic-ai/connector-aiagent/migration-preview/prepare.py "$PREVIEW"
cd "$PREVIEW"
npm ci --ignore-scripts
SINGLE_START=migration ./node_modules/.bin/karma start karma.config.js \
  --no-single-run --auto-watch --browsers Debug \
  --hostname 127.0.0.1 --listen-address 127.0.0.1 --port 19891
```

Open <http://127.0.0.1:19891/debug.html>. The upstream REST fixture stays at the
top; two AI Agent cases sit below it. Select either agent and use
**Template > Migration available > Review changes**, then **Migrate**:

- **Task: Azure OpenAI** — check that Foundry uses Chat Completions and that
  client credentials and the deployment name carry over.
- **Sub-process: Vertex AI** — check that the Gemini Vertex backend keeps the
  project, region, and service-account settings. The custom endpoint is not
  carried over.

Undo or refresh to try a case again. The review dialog comes from the upstream
demo, not released Desktop or Web Modeler.

## Supported moves and release blockers

The draft reads raw BPMN values before replacing the template, then writes them to
active, editable target bindings in order. The preview writes provider, API and
backend selectors first, then authentication selectors and values. It maps
nonempty values without changing their FEEL representation; an empty input
cannot override a target default via a hint.

| Legacy configuration | What the instructions attempt to preserve | What still needs work |
| --- | --- | --- |
| Anthropic | Shared model/timeout fields, public API backend default | An endpoint may be absent, Foundry, or custom. The draft cannot branch on presence or match hostnames. No endpoint or API key is copied, so a custom key is never copied onto the public API binding. |
| Bedrock | Same provider and model; access keys, API key, or default credential chain | Native Converse calls and prompt caching are not verified equivalent. |
| OpenAI | Public backend with Chat Completions, not the v2 Responses default; model, credentials, sampling and timeout | Native SDK request/response behavior is not verified equivalent. |
| Azure OpenAI | Foundry with Chat Completions; API key or client credentials, deployment, timeout and sampling | Authentication and endpoint behavior need runtime verification. |
| OpenAI-compatible | Custom backend with Chat Completions; endpoint, headers, query, body settings, model and sampling | The draft cannot select API-key authentication based on whether a key is set. It does not copy a key or guarantee the no-auth case behaves identically. |
| Vertex AI | Gemini's Vertex backend; service-account JSON or application default credentials, project, region, model, timeout and sampling | The v2 custom Vertex endpoint is `Hidden`, so the draft cannot copy it. |

The demo loads v1 version 13 only. The existing recipes specify a minimum of 12;
the draft matches source IDs without a version floor, or a guard that disables
**Migrate** for an unsupported configuration. If older v1 templates are loaded,
the same action can be offered there. The draft also cannot rewrite FEEL or
combine values. Its **Review changes** action is optional, not a precondition to
**Migrate**. These limitations block a trusted one-click rollout.

The legacy system prompt and OpenAI model defaults differ from v2; explicit
nonempty values are copied. Job type/metadata and other hidden defaults are for
the target template to apply, not for the instructions to restore. The preview
does not validate other common settings across all branches (including response
format, conversation stores, tool wiring, or blank inputs), real deployments,
or provider wire behavior. Schema validation checks template structure, not
those guarantees.
