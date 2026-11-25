# Mixpanel Connector

The Mixpanel Connector records process milestones as Mixpanel events and reads saved Mixpanel
reports. It has no runtime code: it runs as the [REST Connector](./../http/rest)
(`io.camunda:http-json:1`) with its own element template.

## Supported operations

| Operation | Endpoint |
|---|---|
| Import event | `POST /import` ([ingestion API](https://docs.mixpanel.com/reference/import-events)) |
| Query saved report | `GET /api/query/insights` ([query API](https://docs.mixpanel.com/reference/insights-query)) |

- Authentication is a Mixpanel service account (HTTP Basic). Choose a reusable
  `io.camunda.connectors:mixpanel-authentication:1` credential, or enter the service account on the
  task.
- The project ID is a task field. Both APIs need it as a query parameter, and a credential can only
  supply authentication.
- The data residency field picks the US (default), EU or India host for each operation. The two
  APIs live on different hosts.
- Import event sets `time` (now) and `$insert_id` (a UUID) once, when the task starts, so a retried
  task sends the same values and Mixpanel drops the duplicate. Two separate imports get different
  `$insert_id`s, so Mixpanel counts both. Both values can be overridden in the event properties.
  `strict=1` makes Mixpanel reject invalid events with HTTP 400, which fails the job.
- The query API allows 60 queries per hour.

## How the template is built

`element-templates/mixpanel-connector.json` is generated. Never edit it by hand.

1. `src/test/resources/mixpanel-openapi.json` is a trimmed copy of Mixpanel's published ingestion
   and query specs (https://github.com/mixpanel/docs/tree/main/openapi): only the operations above
   and the components they reference, unchanged, merged into one JSON file.
2. `src/test/resources/mixpanel-openapi-overlay.json` is an
   [OpenAPI Overlay](https://spec.openapis.org/overlay/v1.0.0.html) with Camunda's spec fixes: it
   keeps only service account authentication and removes the Content-Type and Content-Encoding
   header parameters.
3. `GenerateElementTemplate` runs the OpenAPI generator (`openapi-parser`) on the patched spec, then
   curates the result: operation names, data residency, credential, project ID, the input fields and
   the request body. Data residency is curated rather than in the overlay, because each operation
   needs its own host and the generator only reads the spec's top-level servers.
   `GenerateElementTemplateTest` fails when the committed template is stale.

Regenerate the template after changing the generator or the overlay:

```shell
mvn -pl connectors/mixpanel test-compile exec:java \
  -Dexec.mainClass=io.camunda.connector.mixpanel.GenerateElementTemplate \
  -Dexec.classpathScope=test
```

Refresh the pinned spec, then regenerate and review both diffs:

```shell
mvn -pl connectors/mixpanel test-compile exec:java \
  -Dexec.mainClass=io.camunda.connector.mixpanel.RefreshOpenApiSpec \
  -Dexec.classpathScope=test
```

To add an operation, add it to `GenerateElementTemplate.OPERATIONS`, refresh the spec and
regenerate.

The end-to-end tests are in
[`connectors-e2e-test-mixpanel`](../../connectors-e2e-test/connectors-e2e-test-mixpanel). They
apply the template and run it against a WireMock stand-in for the Mixpanel API.
