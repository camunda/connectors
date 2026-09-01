# Databricks Connector

This module ships two element templates, both JSON-only specializations of already-shipping connectors — no Java code, no separate runtime:

- **Databricks** (`databricks-connector.json`) — the [Databricks REST API](https://docs.databricks.com/api/workspace/introduction): SQL Statement Execution, SQL Warehouses, Jobs, Model Serving, and Vector Search. Reuses the [HTTP JSON Connector](../http/rest).
- **Databricks Genie (MCP)** (`databricks-genie-mcp-connector.json`) — natural-language questions over a Genie space via [Databricks' managed Genie MCP server](https://docs.databricks.com/aws/en/agents/mcp-tools/genie-mcp). Reuses the agentic-ai [MCP Remote Client connector](../agentic-ai). See [Databricks Genie (MCP)](#databricks-genie-mcp) below.

The two exist separately because their auth models differ (service principal vs. on-behalf-of-user) and because MCP, not REST, is Databricks' required path for chat/agent-facing Genie integrations — see [Limitations](#limitations) below for why the REST template excludes Genie.

## Supported operations

Pick a **Databricks API**, then an **Operation**. The form shows only the fields that operation needs; the URL, HTTP method, and query parameters are derived automatically.

### SQL Statement Execution

| Operation | Request |
| --- | --- |
| Execute statement | `POST /api/2.0/sql/statements` |
| Get statement status and result | `GET /api/2.0/sql/statements/{statement_id}` |
| Get result chunk | `GET /api/2.0/sql/statements/{statement_id}/result/chunks/{chunk_index}` |
| Cancel statement | `POST /api/2.0/sql/statements/{statement_id}/cancel` |

### SQL Warehouses

| Operation | Request |
| --- | --- |
| Get warehouse | `GET /api/2.0/sql/warehouses/{id}` |
| Start warehouse | `POST /api/2.0/sql/warehouses/{id}/start` |
| Stop warehouse | `POST /api/2.0/sql/warehouses/{id}/stop` |

### Jobs

| Operation | Request |
| --- | --- |
| Run job now | `POST /api/2.2/jobs/run-now` |
| Get run | `GET /api/2.2/jobs/runs/get` |
| Get run output | `GET /api/2.2/jobs/runs/get-output` |
| Cancel run | `POST /api/2.2/jobs/runs/cancel` |

### Model Serving

| Operation | Request |
| --- | --- |
| Invoke chat / LLM endpoint | `POST /serving-endpoints/{name}/invocations` |
| Invoke custom model (raw payload) | `POST /serving-endpoints/{name}/invocations` |
| Get endpoint | `GET /api/2.0/serving-endpoints/{name}` |

### Vector Search

| Operation | Request |
| --- | --- |
| Query index | `POST /api/2.0/vector-search/indexes/{index_name}/query` |

> Model Serving **invocations** are the one path with no `/api/2.0` prefix. Everything else, including *Get endpoint*, is prefixed.

## Common patterns

**Run a SQL statement that takes longer than 50s.** *Execute statement* with `wait_timeout` of `0s` (or `CONTINUE` on timeout) returns a `statement_id` in a non-terminal state. Loop *Get statement status and result* behind a BPMN timer until `status.state` is terminal. If a response carries `result.next_chunk_index`, page the rest with *Get result chunk*.

**Trigger a job and wait for it.** *Run job now* returns a `run_id`. Poll *Get run* until `state.life_cycle_state` is one of the three terminal values — `TERMINATED`, `SKIPPED`, or `INTERNAL_ERROR` — not just `TERMINATED`; a loop that only checks for `TERMINATED` polls forever after the other two. Only once terminal does `state.result_state` (`SUCCESS`, `FAILED`, `TIMEDOUT`, `CANCELED`, etc.) become available to branch on. **For a multi-task job, `Get run output` needs an individual task's `run_id`** — read it from the terminal *Get run* response's `tasks[].run_id`, not the top-level `run_id` *Run job now* returned; Databricks only accepts a single-task run there. *Cancel run* covers BPMN-side cancellation or a boundary timer.

**Retries default to 0.** Execute statement and Run job now are non-idempotent: a retry resends the identical request. The SQL Statement Execution API has no idempotency key at all, so a retried *Execute statement* can re-run an `INSERT`/`UPDATE` with no way to detect or suppress the duplicate. *Run job now* does accept one — set **Idempotency token** to something stable per process instance and Databricks returns the existing run instead of starting a new one — but only once you've set it; raising **Retries** above 0 without that is what causes duplicate runs. For read-only operations (Get run, Get warehouse, Get statement status, etc.) it's safe to raise retries on that task.

**Warm the warehouse first.** *Execute statement* against a stopped warehouse waits for it to start. To control that explicitly, call *Start warehouse* and poll *Get warehouse* until `state` is `RUNNING`. Start and stop return immediately and do not wait for the transition.

## Authentication

| Type | Use |
| --- | --- |
| OAuth M2M (service principal) | **Recommended for production.** Client credentials are sent as a Basic Auth header to `https://<workspace>/oidc/v1/token` with `scope=all-apis`. Access tokens are valid for one hour. |
| Personal access token | Testing only. |

The OAuth token endpoint is derived from the workspace URL, so it does not have to be configured separately.

> **OAuth U2M with PKCE is not supported.** Databricks does document a manual authorization-code + PKCE flow for third-party apps (register a custom OAuth application, request `scope=all-apis offline_access` at `/oidc/v1/authorize`, exchange the code at `/oidc/v1/token` for a refresh token) — the gap isn't a missing token endpoint, it's that the *initial* authorization step needs an interactive browser redirect from a human, which an unattended Connector job worker cannot perform. Once obtained, the refresh token would also need external rotation this Connector does not do. Use OAuth M2M for unattended production workloads instead.

## Partner telemetry

Every request carries the User-Agent header required for Databricks Technology Partner attribution, in the documented `<isv-name_product-name>/<product-version>` format:

```
User-Agent: Camunda_DatabricksConnector/1.0
```

Additional headers can be supplied, but the mandated `User-Agent` value is merged last and therefore cannot be overridden.

## Error handling

The Databricks SQL Statement Execution API returns **HTTP 200 with `status.state = FAILED`** when a statement fails at the warehouse, so a plain HTTP success check is not sufficient. The template ships a default error expression that raises a BPMN error for the terminal failure states `FAILED`, `CANCELED`, and `CLOSED`. `PENDING` and `RUNNING` are deliberately **not** errors — they mean the statement is still executing. This expression sees only the response body, not which operation produced it, so it also checks for the SQL-specific `statement_id` field before interpreting `status.state` — without that guard, a *Invoke custom model (raw payload)* response happening to contain its own `status.state` (a custom model's own field, unrelated to Databricks) would be misread as a SQL failure.

**Job run outcomes are not covered by that expression.** A failed run is reported in `state.result_state` on *Get run*, and polling loops normally branch on it with a gateway rather than throwing. Add it to the error expression only if you want a failed run to become a BPMN error.

## Limitations

- **Polling is modelled in BPMN, not inside the Connector.** Each operation is a single HTTP call. Wait/retry loops for statements, job runs, and warehouse state use a BPMN timer and gateway.
- **`INLINE` SQL results are capped at 25 MiB**; exceeding the cap aborts the statement without a result set. Use `EXTERNAL_LINKS` for larger results — its presigned URLs expire after 15 minutes and must be fetched **without** an `Authorization` header, which means a separate plain HTTP task rather than this Connector.
- **`format` is fixed to `JSON_ARRAY`**, which is valid with both dispositions. `ARROW_STREAM` and `CSV` require `EXTERNAL_LINKS`.
- **`stream` is forced to `false`** for chat endpoints. A streamed `text/event-stream` response cannot be consumed by a synchronous Connector.
- **Statements expire.** Roughly 12 hours after reaching a terminal state a statement is removed, and *Get statement* / *Get result chunk* then return HTTP 404.
- **Jobs uses API 2.2.** The older 2.1 endpoints are not exposed.
- **Genie Conversation API is not included.** It requires a multi-call poll-until-terminal loop over conversation state, and the Partner Well-Architected Framework directs chat-based integrations to Genie via MCP rather than REST. See [Databricks Genie (MCP)](#databricks-genie-mcp) below for that path.
- Write operations beyond those listed (creating jobs, editing warehouses, deleting endpoints) are intentionally not exposed.

## Requirements

- A Databricks workspace, and a SQL warehouse, job, Model Serving endpoint, or vector search index to target
- For OAuth M2M: a Databricks service principal with an OAuth secret, granted access to the resources it calls
- Credentials stored as Connector secrets, referenced as `{{secrets.NAME}}`

## API documentation

- [SQL Statement Execution API](https://docs.databricks.com/api/workspace/statementexecution/executestatement)
- [SQL Warehouses API](https://docs.databricks.com/api/workspace/warehouses/get)
- [Jobs API 2.2](https://docs.databricks.com/api/workspace/jobs/runnow)
- [Model Serving — query endpoint](https://docs.databricks.com/api/workspace/servingendpoints/query)
- [Vector Search — query index](https://docs.databricks.com/api/workspace/vectorsearchindexes/queryindex)
- [OAuth M2M for service principals](https://docs.databricks.com/aws/en/dev-tools/auth/oauth-m2m)

---

## Databricks Genie (MCP)

Lets a BPMN process ask a [Databricks Genie](https://docs.databricks.com/aws/en/genie/index) space a natural-language question, over Databricks' managed Genie MCP server, using the agentic-ai [MCP Remote Client connector](../agentic-ai) underneath (`databricks-genie-mcp-connector.json`). No code in `connectors/agentic-ai/` was changed — this template only pre-configures that connector's existing inputs.

### Why this is a separate template, not an option on the REST template above

The REST template's own limitations section already says Genie needs MCP, not REST, for chat/agent-facing use (per Databricks' Partner Well-Architected Framework). This template is that MCP path. It cannot simply reuse the REST template's OAuth M2M block, because **the auth models are genuinely incompatible** — see below.

### Tool surface

Confirmed against `docs.databricks.com/aws/en/agents/mcp-tools/genie-mcp` (not assumed): the Genie MCP server exposes five tools, not the one or two guessed in earlier research passes on this epic. This template covers four of them via a **Genie operation** dropdown; the fifth, `view_ask`, only works with MCP-Apps-compatible interactive clients and doesn't apply to a headless BPMN task, so it's intentionally excluded.

| Genie operation | MCP tool | Notes |
| --- | --- | --- |
| Ask a question | `genie_ask` | Starts a turn. Returns a `conversation_id` and `response_id`; async, not blocking. |
| Poll for a response | `genie_poll_response` | Call repeatedly until `status` is terminal. |
| Get full query result | `genie_get_query_result` | Full SQL result beyond the truncated default. |
| Cancel response | `genie_cancel_response` | Cancel an in-flight turn. |

**This needs a poll loop, modelled in BPMN** — the same pattern as the SQL Statement Execution and Jobs loops in the REST template above: *Ask a question*, then loop *Poll for a response* behind a BPMN timer until `status` is terminal.

Endpoint: a single Genie space at `https://<workspace>/api/2.0/mcp/genie/{genie_space_id}`, or the whole workspace ("Genie One") at `https://<workspace>/api/2.0/mcp/genie` with no space id — a **Genie scope** field picks between them. Databricks documents no space-listing API, so there is nothing here to discover a space id for you; copy it from the space's URL in the workspace.

**Tool argument and response field names are not formally published.** Databricks' docs are prose-only for these five tools — no parameter table, no example request/response body. `question`, `conversation_id`, and `response_id` (used by this template) are the best-corroborated guesses, not a confirmed schema. The **Genie operation** field carries an always-visible note saying so, and points at this template's own "List Tools" operation choice as the way to check the live schema against your endpoint before relying on it.

### Authentication — deliberately not OAuth M2M

Confirmed against Databricks' docs (`genie-mcp`, `managed-mcp`, and `connect-clients` pages, independently, Aug 2026): **"Service principals aren't supported"** for Genie MCP. It requires on-behalf-of-user OAuth (interactive authorization-code + PKCE, a human identity) or a personal access token — there is no server-to-server path.

This directly contradicts this feature's own epic ([camunda/experience-pdp#51](https://github.com/camunda/experience-pdp/issues/51)), which assumed OAuth M2M "appears to be met already" — that assumption is now known to be false, not merely undecided. The template's authentication dropdown is narrowed accordingly to **None** (local/mock testing) and **Bearer token**, dropping OAuth 2.0 client-credentials entirely rather than leaving a guaranteed-to-fail option in the UI. The token is a human-obtained OAuth user access token or a PAT, stored as `{{secrets.DATABRICKS_GENIE_ACCESS_TOKEN}}` — a name distinct from the REST template's `DATABRICKS_CLIENT_SECRET`/`DATABRICKS_TOKEN`, because it is a different kind of credential (a human identity's token, not a service principal's secret) and conflating the names would invite rotating the wrong one.

**This token is not refreshed by the connector.** A user access token is typically valid for about an hour; a long-running unattended process needs an external mechanism to rotate the secret. There is currently no way to run this template against Genie MCP with a purely unattended, always-valid credential.

### Attribution — a limitation, not something this template solves

Databricks requires anything that surfaces a Genie answer to display "Powered by Genie" plus a citation linking to the source Genie Space. This connector only writes process variables; it cannot render that label. The **Genie space ID** (and any space/conversation link the tool response happens to include) is available to pull into the **Result expression**, so whatever renders the answer — a Camunda form, Tasklist, or a custom app — can display it. Who owns actually rendering that label is an open question on experience-pdp#51 and is not decided here.

### What was and wasn't verified

- Tool names, endpoint paths, OAuth scope (`genie`), and the service-principal restriction: confirmed against fetched Databricks documentation.
- Tool argument/response field names: not confirmed (Databricks publishes no schema for them) — best-effort, flagged in the template.
- The `isError` field checked by the default error expression: confirmed from this repo's own `McpClientCallToolResult` source, not guessed.
- The element template's JSON Schema validity and its FEEL expressions (URL derivation, tool name/argument derivation, error guard) were checked with `ajv` and `feelin` respectively — `feelin` is close to Zeebe's Scala FEEL engine but not verified byte-identical.
- **No live call was made against a real Databricks Genie MCP endpoint.** Nothing here has been observed on the wire.
