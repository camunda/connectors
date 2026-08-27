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

**Trigger a job and wait for it.** *Run job now* returns a `run_id`. Poll *Get run* until `state.life_cycle_state` is `TERMINATED`, then branch on `state.result_state`, and read task output with *Get run output*. *Cancel run* covers BPMN-side cancellation or a boundary timer.

**Avoid duplicate job runs.** The template defaults to 3 retries. A retried *Run job now* would start the job twice, so set **Idempotency token** to something stable per process instance — Databricks then returns the existing run instead of starting a new one.

**Warm the warehouse first.** *Execute statement* against a stopped warehouse waits for it to start. To control that explicitly, call *Start warehouse* and poll *Get warehouse* until `state` is `RUNNING`. Start and stop return immediately and do not wait for the transition.

## Authentication

| Type | Use |
| --- | --- |
| OAuth M2M (service principal) | **Recommended for production.** Client credentials are sent as a Basic Auth header to `https://<workspace>/oidc/v1/token` with `scope=all-apis`. Access tokens are valid for one hour. |
| Personal access token | Testing only. |

The OAuth token endpoint is derived from the workspace URL, so it does not have to be configured separately.

> **OAuth U2M with PKCE is not supported**, and neither is an OAuth refresh-token option. The interactive authorization-code + PKCE flow requires a browser redirect, and a Connector executes unattended in a job worker. Databricks also has no documented way to obtain a standalone refresh token for third-party use outside its own CLI, so there is no out-of-band variant either — use OAuth M2M for unattended production workloads.

## Partner telemetry

Every request carries the User-Agent header required for Databricks Technology Partner attribution, in the documented `<isv-name_product-name>/<product-version>` format:

```
User-Agent: Camunda_DatabricksConnector/1.0
```

Additional headers can be supplied, but the mandated `User-Agent` value is merged last and therefore cannot be overridden.

## Error handling

The Databricks SQL Statement Execution API returns **HTTP 200 with `status.state = FAILED`** when a statement fails at the warehouse, so a plain HTTP success check is not sufficient. The template ships a default error expression that raises a BPMN error for the terminal failure states `FAILED`, `CANCELED`, and `CLOSED`. `PENDING` and `RUNNING` are deliberately **not** errors — they mean the statement is still executing.

**Job run outcomes are not covered by that expression.** A failed run is reported in `state.result_state` on *Get run*, and polling loops normally branch on it with a gateway rather than throwing. Add it to the error expression only if you want a failed run to become a BPMN error.

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
