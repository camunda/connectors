# Databricks Connector

The **Databricks Connector** lets a BPMN process call the [Databricks REST API](https://docs.databricks.com/api/workspace/introduction) — SQL Statement Execution, SQL Warehouses, Jobs, Model Serving, and Vector Search.

This Connector reuses the base implementation of the [HTTP JSON Connector](../http/rest) by providing a compatible element template. There is no Java code and no separate runtime to deploy.

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

**Raise Job timeout alongside Read timeout for slow calls.** Model Serving allows up to 597s of model execution, well beyond SQL's own `wait_timeout`, which is capped at 50s. If **Job timeout** (Zeebe's own job activation timeout) stays at its default while **Read timeout in seconds** is raised to cover a slow Model Serving call, Zeebe can decide the job timed out and reactivate it on another worker while the first HTTP request is still in flight — a duplicate non-idempotent call that `Retries = 0` does not prevent, because it happens outside the connector entirely. Raise both together.

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

The Databricks SQL Statement Execution API returns **HTTP 200 with `status.state = FAILED`** when a statement fails at the warehouse, so a plain HTTP success check is not sufficient. The terminal states are:

| State | Meaning |
| --- | --- |
| `SUCCEEDED` | Execution successful, result available for fetch. |
| `FAILED` | Execution failed; the reason is in `status.error.message`. |
| `CANCELED` | Cancelled explicitly, or by `on_wait_timeout=CANCEL`. |
| `CLOSED` | **Success.** "Execution successful, and statement closed; result no longer available for fetch." |

`PENDING` and `RUNNING` are not terminal — they mean the statement is still executing, and the result must be polled with *Get statement status and result*.

**Branch on the state with a gateway, not with an error expression.** Map the state into a variable in the **Result expression**, then route on it:

```
Result expression:
  {
    sqlState: response.body.status.state,
    sqlError: if response.body.status.error = null then null else response.body.status.error.message,
    rows: if response.body.result = null then null else response.body.result.data_array
  }

Exclusive gateway:
  =sqlState = "FAILED"    -> error handling path
  =sqlState = "CANCELED"  -> cancellation path
  (default)               -> continue
```

**Why not an error expression?** The template deliberately ships **no default error expression**. An error expression is evaluated against the *mapped output* whenever **Result variable** or **Result expression** is set, not against the raw response — so an expression written against `response.body.status.state` evaluates `response.body` as `null` and silently never fires, exactly when a task is configured the normal way. A failed statement would then complete as a success. (Verified against a real workspace: with a result mapping the expression's `response` is the mapped variable map; without one it is the raw HTTP result.)

If you do write your own error expression anyway, write it against whatever your result mapping produces, and guard on the SQL-specific `statement_id` — the expression cannot tell which operation produced the response, so an *Invoke custom model (raw payload)* response that happens to carry its own `status.state` would otherwise be misread as a SQL failure.

**Job runs work the same way.** A failed run is reported in `state.result_state` on *Get run* (`SUCCESS`, `FAILED`, `TIMEDOUT`, `CANCELED`, …), available only once `state.life_cycle_state` is terminal. Map it out and branch on it with the same gateway pattern.

## Limitations

- **Polling is modelled in BPMN, not inside the Connector.** Each operation is a single HTTP call. Wait/retry loops for statements, job runs, and warehouse state use a BPMN timer and gateway.
- **`INLINE` SQL results are capped at 25 MiB**; exceeding the cap aborts the statement without a result set. Use `EXTERNAL_LINKS` for larger results — its presigned URLs expire after 15 minutes and must be fetched **without** an `Authorization` header, which means a separate plain HTTP task rather than this Connector.
- **`format` is fixed to `JSON_ARRAY`**, which is valid with both dispositions. `ARROW_STREAM` and `CSV` require `EXTERNAL_LINKS`.
- **`stream` is forced to `false`** for chat endpoints. A streamed `text/event-stream` response cannot be consumed by a synchronous Connector.
- **Statements expire.** Roughly 12 hours after reaching a terminal state a statement is removed, and *Get statement* / *Get result chunk* then return HTTP 404.
- **Jobs uses API 2.2.** The older 2.1 endpoints are not exposed.
- **Genie Conversation API is not included.** It requires a multi-call poll-until-terminal loop over conversation state, and the Partner Well-Architected Framework directs chat-based integrations to Genie via MCP rather than REST.
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
