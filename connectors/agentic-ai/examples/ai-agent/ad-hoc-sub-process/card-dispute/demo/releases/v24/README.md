# Card dispute demo

Unsupported prototype for [#137](https://github.com/camunda/agentic-orchestration-pod/issues/137).
It runs on the prototype cluster built from source (`../cluster.sh`, REST and UIs on `http://localhost:8280`, connectors on `:8286`, c8ctl profile `local-proto`). Engine and connectors are 8.11 prototype branches, so most 8.11 capabilities run for real. What is still a stand-in is marked in the diagram with a text annotation. All data is synthetic.

## What it demonstrates

A bank analyst gets a card dispute. The dispute agent reads the statement (a PDF, parsed by OpenRouter) and the transaction export, follows a versioned compliance policy, uses three tools, and drafts a decision. The analyst accepts the draft or sends it back with feedback, and the agent revises.

| Step                            | 8.11 epic                                                                                                   | Treatment in this prototype                                                |
|---------------------------------|-------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| Statement and export as input   | [Document flow (experience-pdp#81)](https://github.com/camunda/experience-pdp/issues/81)                     | Live: documents in the user prompt; `ReconcileTransactions` resolves the export by file name and reads it with `GET /v2/documents/{id}`; the review form previews both documents. |
| Compliance policy prompt        | [Governed external instructions (experience-pdp#75)](https://github.com/camunda/experience-pdp/issues/75)    | Live: the policy reaches the agent only through `linkedResources` (binding `latest`). Operate shows Prompt ID, Binding and Version. `proof/binding-proof.py` shows `deployment` binding pins a running instance. |
| Read the card statement (PDF)   | [Server tools (experience-pdp#83)](https://github.com/camunda/experience-pdp/issues/83)                      | Live: the statement is a PDF in the start request. OpenRouter parses it provider-side through the `file-parser` **plugin** (engine `native`), configured with `bodyProperties.plugins`. This is a plugin, not an `openrouter:*` server tool: OpenRouter has no PDF server tool. Operate shows a "PDF parsing" card; see "PDF statement". |
| Read card network rules         | [Server tools (experience-pdp#83)](https://github.com/camunda/experience-pdp/issues/83)                      | Web search is off. The rules are part of the configured chargeback-deadline Skills ZIP, read in Daytona. |
| Date arithmetic                 | [AI Agent Sandbox (experience-pdp#82)](https://github.com/camunda/experience-pdp/issues/82)                  | Live: CREATE materializes the configured Skills ZIP. The agent reads its instructions and rules, then executes its original Python with matching pre/post hashes. Sandbox failures return explicit `SANDBOX_FAILED`; there is no fallback deadline. |
| External fraud opinion          | [A2A 1.0 client GA (experience-pdp#113)](https://github.com/camunda/experience-pdp/issues/113)               | Live: a real A2A fraud agent (`fraudAgentUrl`, default `http://localhost:38090`) with bearer auth (connector secret `FRAUD_AGENT_TOKEN`). One conversation per run. |
| Saved LLM credential            | [#86](https://github.com/camunda/experience-pdp/issues/86)                                                   | Live: the agent binds `camunda.vars.env.cardDisputeLlm`; no LLM secret in the BPMN. |

## Files

- `card-dispute.bpmn`: the process. The agent is the AI Agent Sub-process (template v2) on OpenRouter.
- `review-dispute.form`: the analyst review form.
- `policies/dispute-compliance-policy.md`: policy v1, cold/professional tone, auto-approve up to $500.
- `policies/v2/dispute-compliance-policy.md`: policy v2, identical rules and threshold, warm/friendly tone.
- `skills/chargeback-deadline.zip`: configured Skills bundle; its three entries must match `skills/chargeback-deadline/` exactly. The starter checks the bytes before any uploads.
- `data/`: the synthetic statement (`statement-2026-09.pdf`, generated from `statement-2026-09.txt`), the export as CSV, and the export as JSON for the reconciliation tool.
- `scripts/make-statement-pdf.py`: regenerates the statement PDF.
- `scripts/demo.sh`: deploy, switch the policy, and start a dispute.
- `scripts/credential.sh`: creates the saved LLM credential `cardDisputeLlm`.
- `scripts/web-search.sh`: the #83 switch (off by default).
- `proof/binding-proof.py`: the #75 deployment-binding proof.

## Run it

Prerequisites: the fraud agent stack `camunda-a2a-e113` running (A2A proxy on `localhost:38090`), the prototype cluster (`../cluster.sh start`), c8ctl 4.x with profile `local-proto`, the connector secrets `DAYTONA_API_KEY` and `FRAUD_AGENT_TOKEN` (`../cluster.sh` reads the token from `FRAUD_AGENT_TOKEN_FILE`, default `../../epic-113/a2a-fraud-token.secret`), and the saved LLM credential.

The credential is created once per cluster with `scripts/credential.sh`. It copies `CAMUNDA_PROVIDED_LLM_API_ENDPOINT`, `_API_KEY` and `_DEFAULT_MODEL` from your environment into the engine's file secret store (`../run/secrets`, mode 700) and creates the global cluster variable `cardDisputeLlm` (kind `SECRET_REFERENCE`, template `io.camunda:agentic-ai-openai-custom-credential:1`). Its value holds only `camunda.secrets.*` references. The engine resolves them into the job activation only, so Operate never shows the key. Values are never printed.

```sh
scripts/demo.sh deploy      # process, form, policy v1 (also resets to v1 before a rehearsal)
scripts/demo.sh start       # expect APPROVE for $482.17
scripts/demo.sh policy-v2   # deploys only the Markdown policy
scripts/demo.sh start       # same dispute, expect APPROVE with warm/friendly tone
scripts/demo.sh policy-v1   # reset before the next rehearsal
```

Then open Tasklist to review the draft, and Operate to compare both instances. `./rehearse.sh --dry-run` checks the cluster, policy version, and fraud agent card first.

## Configured skill and document previews

`AnalyzeInSandbox` binds the Skills field (`data.skills`) to `deadlineSkillBundles`, a list containing the uploaded ZIP reference. Startup script is empty. CREATE extracts the three original files under `<CREATE.workDir>/.agents/skills/chargeback-deadline` and returns the skill catalog.

The agent must read configured `SKILL.md` and `references/network-rules.md` before running the configured script, without replacement imports or modifications. In v24, the skill instructions and their preview describe configured ZIP delivery as well. Only the delivery instructions changed; `deadline.py` remains byte-identical with SHA-256 `816444bd5c28a64e7ff99c7de4bb6c706e42b3bfe56acc1c98e9089c07fa8416`. Python runs from `CREATE.workDir` using `.agents/skills/chargeback-deadline/scripts/deadline.py`. Evidence includes the ZIP reference/hash, catalog, actual paths, matching pre/post script hashes, and complete actual JSON stdout. No new image, dependencies, or generated script is used.

The five individual documents remain attached for evidence and Operate previews; they do not replace the configured runtime files. Operate renders `.md` as formatted Markdown and `.py` as literal plain text. PDF previews use the original document URL, including store ID and content hash.

The isolated cluster now uses the built-in local document store at `../run/documents`. The store retains the historical ID `in-memory` so existing references remain valid, but its contents are disk-backed. It has no automatic expiry; original metadata and hashes are preserved. Do not delete this directory before the demo. The former fallback in-memory store lost uploads on engine restart; a long TTL would not have fixed that. Ninety still-retrievable documents were preserved during migration; older missing documents were not silently substituted. Observation-masking memory remains a separate volatile runtime store.

`releases/v22/` preserves the pre-configured-ZIP model, policies, skill bytes, driver, templates, and runtime attestation as a fallback. It is not a claim of full v22 certification.

`releases/v23/` preserves the first configured-ZIP version and its original import-era skill text. Its reference manifest explicitly records the post-capture completion-field error and repeated A2A call; only the initial captured review is the successful configured-skill reference stage.

## PDF statement (#83)

`scripts/demo.sh start` and `rehearse.sh` upload `data/statement-2026-09.pdf` (`application/pdf`) and the CSV export with the start request. The agent sends the PDF to OpenRouter as an `input_file` content part, and the `file-parser` plugin parses it during the model call.

- Engine: the EU endpoint accepts only `native` (or no plugin). `cloudflare-ai` and `mistral-ocr` return 400 "PDF parsing in this data region requires the native engine".
- Proof that parsing ran: the PDF contains two facts found in no other file, "Statement ID: AP-STM-7Q4K-0926" and "Rewards points earned this period: 1,737 (balance 18,204)". The system prompt asks the agent to quote both in an evidence item starting with "Card statement (PDF):".
- Operate: the agent history records `documentParsing` (plugin, engine, how it was sent, and the document name and ID). The native engine returns no parser annotations, so this records what was sent and configured, not a provider receipt.
- Regenerate the PDF after editing the txt: `python3 scripts/make-statement-pdf.py`.

## Web search switch (#83)

`openrouter:web_search` needs the global OpenRouter endpoint. Data residency is approved for the demo, but the current key is blocked: its OpenRouter workspace guardrail allows only `eu.openrouter.ai` (global returns 403), and the EU endpoint rejects both the server tool and the web plugin. An OpenRouter workspace admin must allow the global region, or a key from another workspace is needed. Then:

```sh
export GLOBAL_LLM_API_ENDPOINT=https://openrouter.ai/api/v1 GLOBAL_LLM_API_KEY=... GLOBAL_LLM_DEFAULT_MODEL=...
scripts/web-search.sh on    # credential cardDisputeLlmGlobal + variants/card-dispute-web-search.bpmn
scripts/web-search.sh off   # back to the default model
```

The older variant switches the agent to the completions API, adds the `web_search` server tool (mastercard.com, 3 results), and asks for cited URLs. It is not the current configured-skill reference model.

## Daytona mock and explicit failure (#82)

- Mock hook: set the start variable `daytonaApiUrl` to point the sandbox connector (`data.apiUrl`) at a stub. Then `data.apiKey` is `daytonaApiKey` if set, otherwise the dummy `mock-daytona-key`, so the real key never reaches a stub. Unset means real Daytona with `DAYTONA_API_KEY`.
- Failure: `AnalyzeInSandbox` maps connector errors to BPMN error `SANDBOX_UNAVAILABLE`. Its boundary returns `{error: "SANDBOX_FAILED", message: "The sandbox call failed. The filing deadline is not computed."}`. There is no outgoing fallback calculation. Successful task-local results have an explicit output mapping so stale parent results cannot be collected.
- Force an explicit failure without using Daytona: `EXTRA_VARS='{"daytonaApiUrl":"http://localhost:9"}' ./rehearse.sh` (also works with `scripts/demo.sh start`).

## Known limits

- `systemPromptBinding` is a static task header that labels the binding. Keep it equal to `bindingType` of the linked resource; the engine does not copy the binding type into the job.
- Live model output varies between runs. Record a backup run before presenting.
- The model sometimes wraps its JSON in prose and a code fence. The review task falls back to extracting the fenced JSON.
- The model must not calculate dates itself. The configured skill's verified stdout is the deadline evidence; the reference case returns `2027-01-13`.
- The fraud agent runs on a separate local stack. If it is down, the draft escalates with "fraud opinion unavailable".

## Fallback stack

The earlier alpha4 stack with the connectors #7632 runtime stays available at `http://localhost:8180/operate` (`/Users/eric.lundberg/GitHub/card-dispute-sandbox-patch/isolation/stack.sh start`, `isolation/demo.sh deploy|start`). It runs the sandbox live but not #75, #81 or #86.
