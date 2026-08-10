# AGENTS.md

## Role & boundary

Camunda Connectors is the connector ecosystem for Camunda Platform 8: an SDK for building connectors,
a runtime that executes them, and 30+ out-of-the-box connectors (AWS, HTTP, Kafka, etc.). Java 21
(SDK: Java 17). Product docs: https://docs.camunda.io/docs/components/connectors/overview/

Don't overengineer — follow YAGNI and KISS. No abstractions for single-use code, no error handling
for impossible scenarios, no unrequested flexibility. Touch only what the task requires; don't
"improve" adjacent code or delete pre-existing dead code you didn't cause.

**Path map:**

| Module                        | Description                                                        |
|--------------------------------|---------------------------------------------------------------------|
| `connector-sdk/`               | SDK for building connectors (Java 17): `core/`, `validation/`, `test/` |
| `connector-runtime/`           | Execution environment — `connector-runtime-core/`, `-spring/`, `spring-boot-starter-camunda-connectors/` |
| `connectors/`                  | Out-of-the-box connectors (one Maven module per connector)          |
| `element-template-generator/`  | Generates Camunda Modeler element templates from annotations        |
| `apps/`                        | Docker images and runtime application (bundles runtime + connectors) |
| `connector-commons/`           | Shared utilities: `connector-object-mapper/`, `connector-test-utils/`, `http-client/` |
| `secret-providers/`            | Secret provider implementations                                     |
| `connectors-e2e-test/`         | End-to-end test modules                                              |
| `docs/adr/`                    | Architecture decision records                                       |

**License split:** Apache 2.0 (SDK, Runtime, HTTP REST connector, Element Template Generator) vs.
Camunda Self-Managed Free Edition (most out-of-the-box connectors) — check a module's `pom.xml`/`LICENSE`
before copying code across the boundary.

**Ask first:** adding dependencies to `pom.xml`, changing SDK public API (`connector-sdk/core`),
modifying shared runtime behavior (`connector-runtime-core/`).

**Never:** decompile `camunda-client-java` / `camunda-spring-boot-starter` — if `camunda/camunda` is
checked out locally (often a sibling directory, e.g. `../camunda`), read the source there instead.

## Build & test

```bash
mvn clean package                    # full build
mvn clean package -Dquickly          # skip long-running tests
mvn test -pl connectors/http/rest -Dtest=HttpJsonFunctionTest   # single test class
mvn verify -pl connectors/kafka      # integration tests (requires Docker)
```

- Root `pom.xml` orchestrates all modules; `parent/pom.xml` defines shared versions/config.
- `e2eExcluded` Maven profile skips E2E tests.
- After changing an SDK model class, regenerate its template via the connector's
  `GenerateElementTemplate` test class.
- Docker-based tests use `io.camunda.connector.test.utils.DockerImages` — add the image to
  `docker-images.properties` in `src/test/resources`, then a constant in `DockerImages`.

## Connector implementation patterns

- **Every connector needs both** `@OutboundConnector`/`@InboundConnector` **and** `@ElementTemplate`
  — missing either is the most common mistake.
- **Operation-based syntax** (preferred for multi-operation connectors): implement
  `OutboundConnectorProvider`, annotate methods with `@Operation`. Single-operation connectors may
  still implement `OutboundConnectorFunction` directly — both are supported.
- Reference implementations: `connectors/http/rest/` (outbound), `connectors/webhook/` (inbound),
  `connectors/kafka/` (both), `connectors/microsoft/azure-blobstorage/` (document handling).
- **Auth types**: model as a `sealed interface` with `@JsonTypeInfo`/`@JsonSubTypes` +
  `@TemplateDiscriminatorProperty` (see any connector's `Authentication` interface for the shape).
- **Property validation**: Jakarta Bean Validation (`@NotEmpty`, `@NotBlank`, `@NotNull`, `@Size`,
  `@Pattern`) is auto-converted to element template constraints.
- **FEEL expressions**: annotate the field with `@FEEL`.
- **Files**: use the `Document` type + `DocumentCreationRequest`, never raw byte arrays, for
  upload/download.
- **ObjectMapper**: always `ConnectorsObjectMapperSupplier.getCopy()`, never a fresh instance —
  it configures `JavaTimeModule`, case-insensitive enums, and lenient array/unknown-property handling.
- **Errors**: throw `ConnectorException` (fatal) or `ConnectorRetryException` (transient, configurable
  retries/backoff); throw `BpmnError` only when a boundary event should catch it.
- **Secrets**: reference them as `{{secrets.NAME}}` in connector inputs; `bindVariables(...)` and
  `bindProperties(...)` resolve them through registered `SecretProvider`s. SDK dependencies stay
  `<scope>provided</scope>` in connector modules.
- **Service registration**: connectors are discovered via `ServiceLoader` —
  `META-INF/services/io.camunda.connector.api.{outbound,inbound}.*` files are required, easy to forget.

## Testing conventions

- `*Test.java` unit, `*InputValidationTest.java`, `*SecretsTest.java`, WireMock (`@WireMockTest`) for
  HTTP integration, `@SlowTest` on anything that shouldn't run in the fast unit-test loop.
- Shared fixtures live in a per-connector `BaseTest` (secrets, context builder, JSON test-case loading)
  — follow the existing pattern rather than inventing a new one per connector.

## Code comments

Before writing an inline comment, name the reader and what they would do differently for having
read it. If you can't name both, don't write it.

## Architecture Decision Records

Read `docs/adr/README.md` for process, template, and the current index before creating or updating one.
Write one when a decision affects multiple modules, involves a real trade-off, or changes an
established SDK/runtime/connector pattern. Sequence number and index entry come from that same README.

## Pull requests & commits

- Use the PR template (`.github/PULL_REQUEST_TEMPLATE.md`).
- PR title should be clear and descriptive; reference the issue number in the description
  (e.g. `closes #1234`).
- Keep PRs focused on a single concern.
- Describe why the change is necessary and note alternatives considered — keep it brief and concise.
- Write for the reader, not the diff: avoid leaking implementation details (variable names, internal
  function names, code structure) into the description — say what changed and why in plain terms;
  the diff already shows the how.
- For bug fixes: ask the engineer whether the fix needs backporting to stable branches before
  opening the PR. If yes, add the `backport stable/X.Y` label(s) when creating it.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/) — see
  `CONTRIBUTING.md#commit-message-guidelines`.
- Backports run through `korthout/backport-action` (`.github/workflows/BACKPORT_PR.yml`) via a
  `backport stable/X.Y` label or a `/backport` comment — never cherry-pick a backport by hand.

## Common pitfalls

- SDK modules require Java 17 even though the rest of the repo is on Java 21 — check inherited
  compiler settings before assuming a module picked up the wrong version.
- File/folder restructuring breaks references silently — check `.github/workflows/`, module
  `README.md`s, and this file for paths that need updating.
- **AWS SDK v1 in `aws-sns`**: the inbound webhook keeps `aws-java-sdk-sns` (v1) for
  `SnsMessageManager` (verifies SNS webhook signatures — no v2 equivalent, see
  [aws-sdk-java-v2#1302](https://github.com/aws/aws-sdk-java-v2/issues/1302)) and for
  `SnsSubscriptionConfirmation#confirmSubscription`. This is the one accepted v1 exception in the
  v1→v2 migration; any other new v1 usage is a regression, not precedent.

## Documentation

Connector-development-pattern, runtime-configuration, or testing-strategy changes should also update
[docs.camunda.io](https://docs.camunda.io/docs/components/connectors/overview/) — open a PR against
`camunda/camunda-docs`.
