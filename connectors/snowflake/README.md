# Snowflake Connector

The Snowflake Connector runs SQL statements on Snowflake through the
[Snowflake SQL API](https://docs.snowflake.com/en/developer-guide/sql-api/index). It has no runtime
code: it runs as the [REST Connector](./../http/rest) (`io.camunda:http-json:1`) with its own element
template.

## Supported operations

These are all the operations of the SQL API.

| Operation | Endpoint |
|---|---|
| Run SQL statement | `POST /api/v2/statements` |
| Get statement status | `GET /api/v2/statements/{statementHandle}` |
| Cancel statement | `POST /api/v2/statements/{statementHandle}/cancel` |

- **Account URL:** every task asks for the account URL in the Authentication section, for example
  `https://myorg-myaccount.snowflakecomputing.com`, without a path, query or fragment. The
  credential holds only the token: the `http-json` credential shape
  (`ServiceAuthenticationConfiguration`) has no URL.
- **Authentication:** a bearer token, either a programmatic access token or an External OAuth
  access token. Choose a reusable `io.camunda.connectors:snowflake-authentication:1` credential, or
  enter the token on the task. The connector does not send `X-Snowflake-Authorization-Token-Type`:
  the header is optional, and Snowflake detects the token type from the token. Key-pair JWT is not
  supported, because the client must sign the JWT. The `SnowflakeOAuth` implicit flow is not
  supported either.
- **Bindings:** pass process variables in the request body's `bindings` and use `?` in the
  statement. Never concatenate process variables into the SQL. Binding values must be strings,
  also for numbers (`value: string(orderId)`): Snowflake rejects a JSON number with HTTP 422.
- **Async results:** Snowflake waits up to 45 seconds for a result. After that, or with
  `async=true`, Run SQL statement returns HTTP 202 with a `statementHandle`. Get statement status
  returns the result for that handle, one `partition` at a time for large results. While the
  statement still runs, Get statement status also returns HTTP 202 with no rows, so check
  `response.status` before reading `data`. The read timeout
  defaults to 60 seconds so that the 45 second wait fits.
- **Headers:** `Accept: application/json`, `Content-Type: application/json` and
  `User-Agent: Camunda` are fixed and hidden. Snowflake rejects a POST without a JSON content type
  with HTTP 415, even when it has no body, as for Cancel statement.
- **No body on Cancel statement:** the template maps `body` to null, so a process variable named
  `body` is not sent with the cancel request.
- **Retries** default to 0, because a retried statement runs again.
- **Request ID:** Snowflake answers a request ID it has seen before with the earlier result and
  does not run the statement again. Leave it empty, or use a new ID for every statement you submit,
  also across tasks and loop iterations. Reuse an ID only to retry that same submission.

## How the template is built

`element-templates/snowflake-connector.json` is generated. Never edit it by hand.

1. `src/test/resources/snowflake-sqlapi.yaml` is a verbatim copy of the published spec
   (https://github.com/snowflakedb/snowflake-rest-api-specs/blob/main/specifications/sqlapi.yaml).
2. `src/test/resources/snowflake-sqlapi-overlay.json` is an
   [OpenAPI Overlay](https://spec.openapis.org/overlay/v1.0.0.html) with Camunda's changes: the
   per-account server, the two supported token types, and no token type header.
3. `GenerateElementTemplate` runs the OpenAPI generator (`openapi-parser`) on the patched spec, then
   curates the result: operation names, labels, account URL, credential, headers, timeouts and
   defaults. `GenerateElementTemplateTest` fails when the committed template is stale.

Regenerate the template after changing the generator or the overlay:

```shell
mvn -pl connectors/snowflake test-compile exec:java \
  -Dexec.mainClass=io.camunda.connector.snowflake.GenerateElementTemplate \
  -Dexec.classpathScope=test
```

Refresh the pinned spec, then regenerate and review both diffs:

```shell
mvn -pl connectors/snowflake test-compile exec:java \
  -Dexec.mainClass=io.camunda.connector.snowflake.RefreshOpenApiSpec \
  -Dexec.classpathScope=test
```

The end-to-end tests are in
[`connectors-e2e-test-snowflake`](../../connectors-e2e-test/connectors-e2e-test-snowflake). They
apply the template and run it against a WireMock stand-in for the SQL API.
