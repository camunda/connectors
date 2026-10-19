# GenAI tracing backends for the AI Agent

Docker Compose stacks to look at the OpenTelemetry GenAI traces that the AI Agent connector emits
(see [ADR 019](../../docs/adr/019-opentelemetry-genai-tracing.md)). Local development only: no persistence
for Phoenix, dev-only credentials for Langfuse, everything bound to `127.0.0.1`.

| File                                                       | Purpose                                                    |
|------------------------------------------------------------|------------------------------------------------------------|
| [docker-compose.phoenix.yml](docker-compose.phoenix.yml)   | Arize Phoenix `version-20`                            |
| [docker-compose.langfuse.yml](docker-compose.langfuse.yml) | Langfuse `4` self-hosted (web, worker, Postgres, ClickHouse, Redis, MinIO) |
| [application-tracing-phoenix.yaml](application-tracing-phoenix.yaml)   | Spring profile that exports to Phoenix   |
| [application-tracing-langfuse.yaml](application-tracing-langfuse.yaml) | Spring profile that exports to Langfuse  |

## Start a backend

### Arize Phoenix

```bash
docker compose -f docker-compose.phoenix.yml up -d --wait
```

* UI: <http://localhost:6006>
* OTLP/HTTP (protobuf): `http://localhost:6006/v1/traces` (Phoenix rejects OTLP/JSON with HTTP 415)
* OTLP/gRPC: `localhost:4317`
* Traces persist in the `phoenix_data` volume (SQLite). Stop: `docker compose -f docker-compose.phoenix.yml down`; also discard the traces: add `-v`

### Langfuse

```bash
docker compose -f docker-compose.langfuse.yml up -d --wait --wait-timeout 600
```

The first start runs the database migrations and takes a few minutes. It needs roughly 3 GB of free memory in
the Docker VM; the `langfuse-web` container is OOM-killed when the VM is shared with a full Camunda stack.

* UI: <http://localhost:3000>, user `admin@example.com`, password `password` (local-only credentials)
* Organization `Camunda PoC`, project `Camunda AI Agent`, seeded through `LANGFUSE_INIT_*` on first start
* API keys: public `pk-lf-camunda-poc`, secret `sk-lf-camunda-poc`
* OTLP/HTTP: `http://localhost:3000/api/public/otel/v1/traces` (HTTP only, no gRPC)
* Stop and wipe all data: `docker compose -f docker-compose.langfuse.yml down -v`

If port 3000 is taken on your machine (for example by another dev server that listens on `::1`), `localhost`
may resolve to that server rather than to Langfuse. Use `127.0.0.1` in the endpoint.

## Runtime settings

The AI Agent runtime needs these properties (the two profile files in this directory contain them, ready to copy).
Activate one with `SPRING_PROFILES_ACTIVE=tracing-phoenix` or `SPRING_PROFILES_ACTIVE=tracing-langfuse`.

Common to both backends:

```properties
camunda.connector.agenticai.aiagent.tracing.enabled=true
camunda.connector.agenticai.aiagent.tracing.capture-content=true
management.tracing.sampling.probability=1.0
management.opentelemetry.tracing.export.otlp.transport=http
```

* `transport=http` is Boot's value for OTLP over HTTP with protobuf bodies (the default); the alternative is `grpc`.
* Without `sampling.probability=1.0`, Boot samples 10% of traces, so only about one run in ten is exported.
* `capture-content=true` puts prompts, completions, and tool data into the backend. Keep it to local use.

Phoenix:

```properties
management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:6006/v1/traces
```

Langfuse:

```properties
management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:3000/api/public/otel/v1/traces
management.opentelemetry.tracing.export.otlp.headers.Authorization=Basic cGstbGYtY2FtdW5kYS1wb2M6c2stbGYtY2FtdW5kYS1wb2M=
management.opentelemetry.tracing.export.otlp.headers.x-langfuse-ingestion-version=4
```

The `Authorization` value is `Basic base64("pk-lf-camunda-poc:sk-lf-camunda-poc")`:

```bash
echo -n 'pk-lf-camunda-poc:sk-lf-camunda-poc' | base64
# cGstbGYtY2FtdW5kYS1wb2M6c2stbGYtY2FtdW5kYS1wb2M=
```

Set the headers in the profile file (or `SPRING_APPLICATION_JSON`), not through environment variables: the
relaxed environment-variable binding turns the dashes of `x-langfuse-ingestion-version` into dots.

## Runtime recipe (to be verified)

The AI Agent connector ships no OpenTelemetry SDK or exporter. It registers its tracing listener only when
`camunda.connector.agenticai.aiagent.tracing.enabled=true` and an `OpenTelemetry` bean exists, so the runtime
has to provide both. This section is a working draft: the module list was checked against the
Spring Boot 4.1 artifacts on Maven Central, but it has not been run against a stock connector runtime image yet.

1. Add the Spring Boot OpenTelemetry tracing modules and an OTLP exporter to the runtime classpath, at the
   versions that match the runtime's Spring Boot (4.1.1 at the time of writing). On the stock image, drop the
   jars (with their transitive dependencies) into `/opt/custom`. `spring-boot-starter-opentelemetry` 4.1.0
   resolves to these modules, which are the candidates to copy:
   * `org.springframework.boot:spring-boot-opentelemetry` (provides the `OpenTelemetry` SDK bean)
   * `org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry` (SDK tracer provider, sampler,
     and the OTLP trace exporter behind `management.opentelemetry.tracing.export.otlp.*`)
   * `io.micrometer:micrometer-tracing-bridge-otel` (runtime dependency of the starter)
   * `io.opentelemetry:opentelemetry-exporter-otlp` (runtime dependency of the starter)
   * `org.springframework.boot:spring-boot-micrometer-tracing` and `io.micrometer:micrometer-tracing` come in
     transitively
2. Exclude Boot's Observation-to-span bridge so that other connectors, inbound webhooks, and scheduled work stay
   untraced while the agent spans are still exported. The profile files do this with
   `spring.autoconfigure.exclude=org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration`,
   which is the auto-configuration that registers the tracing observation handlers
   (`DefaultTracingObservationHandler` and the propagating sender and receiver handlers). Verify that
   it leaves the SDK, sampler, and exporter in place and that a REST connector call produces no span. Note that
   the property replaces any other `spring.autoconfigure.exclude` list in the runtime.
3. Run an agent and look for the trace in the backend. One trace is one agent run; the session is the agent
   instance.

## Check a backend without the agent

Phoenix accepts only protobuf at `/v1/traces`, so a hand-written JSON span is answered with HTTP 415 there.
Langfuse documents OTLP/JSON and protobuf support. A minimal Langfuse check (not run, see the verification status):

```bash
curl -i -X POST http://localhost:3000/api/public/otel/v1/traces \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Basic cGstbGYtY2FtdW5kYS1wb2M6c2stbGYtY2FtdW5kYS1wb2M=' \
  -H 'x-langfuse-ingestion-version: 4' \
  -d '{"resourceSpans":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"smoke"}}]},"scopeSpans":[{"scope":{"name":"smoke"},"spans":[{"traceId":"5b8efff798038103d269b633813fc60c","spanId":"eee19b7ec3c1b174","name":"chat test","kind":3,"startTimeUnixNano":"1760000000000000000","endTimeUnixNano":"1760000001000000000","attributes":[{"key":"gen_ai.operation.name","value":{"stringValue":"chat"}}]}]}]}]}'
```

## Verification status

* `docker-compose.phoenix.yml`: started and became healthy, accepted an OTLP/protobuf `chat` span at
  `/v1/traces` with HTTP 200, and the span was readable through Phoenix's REST API.
* `docker-compose.langfuse.yml`: **not verified end to end.** The images pull and the dependencies (Postgres,
  ClickHouse, Redis, MinIO) and `langfuse-worker` became healthy, and the database migrations ran, but on the
  verification machine the Docker VM ran out of memory and `langfuse-web` was OOM-killed, so the
  `LANGFUSE_INIT_*` seeding and the OTLP endpoint were not exercised. Treat the init seeding, the web
  healthcheck, and the endpoint as untested.
* The Spring profile files and the runtime recipe were not run against a connector runtime.
