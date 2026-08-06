# Job Lease Support in the Connector SDK — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an outbound connector opt into Zeebe job leasing via `@OutboundConnector(withLease = true)`, and surface the resulting lease token on `JobContext` so connector code can use it.

**Architecture:** `@OutboundConnector.withLease()` flows through `OutboundConnectorConfiguration` (three construction sites: annotation scanning, Spring bean scanning, env-var discovery) into `OutboundConnectorManager`, which sets it on the `JobWorkerValue` handed to the camunda-client job worker. Job completion fencing needs no code change (verified: every command-building call site already uses the `ActivatedJob`-based overloads, which auto-carry the lease token). The token itself is exposed via a new `JobContext.getLeaseToken()` default method, implemented in the production `ActivatedJobContext` (delegates to `ActivatedJob.getLeaseToken()`) and the test-support `TestJobContext` (plain field).

**Tech Stack:** Java 21 (connector-runtime modules) / Java 17 (connector-sdk), Maven multi-module, JUnit 5, Mockito, AssertJ.

## Global Constraints

- No dependency version bump needed — `parent/pom.xml` already pins `camunda-client-java` / `camunda-spring-boot-starter` at `8.10.0-SNAPSHOT`, and that snapshot already contains `JobWorkerValue.withLease`, `ActivatedJob.getLeaseToken()`, and `CompleteJobCommandStep1.withLeaseToken()` (verified 2026-08-06 against `camunda/camunda` main, merge commit `f64cc064e1e8f79ce47b020049558f4e143a9a4d`).
- Default for `withLease` is `false` (per issue #8044).
- `JobContext.getLeaseToken()` must be a **default method**, not abstract — `JobContext` is public SDK API in `connector-sdk/core`, and a default method avoids a source-breaking change for any out-of-tree implementer.
- Out of scope (per user decision): flipping `withLease = true` on any AI Agent connector function's own `@OutboundConnector` annotation, and any AI Agent connector logic that reads/forwards the token for its own history/metrics requests. Do not touch any file under `connectors/agentic-ai/`.
- Out of scope: no new environment-variable or property override specific to Connectors for `withLease` — the upstream `camunda.client.worker.override.<type>.with-lease` property already covers the operator-override case (verified: `SourceAware` priority ordering guarantees it always wins over `FromAnnotation`, regardless of value).
- Full spec: `docs/superpowers/specs/2026-08-06-job-lease-sdk-design.md`.

---

### Task 1: `withLease` on `@OutboundConnector`, plumbed through `OutboundConnectorConfiguration`

**Files:**
- Modify: `connector-sdk/core/src/main/java/io/camunda/connector/api/annotation/OutboundConnector.java`
- Modify: `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/config/OutboundConnectorConfiguration.java`
- Modify: `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/ConnectorConfigurationUtil.java:58-63`
- Modify: `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/outbound/DefaultOutboundConnectorFactory.java:221-227`
- Modify: `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/discovery/EnvVarsConnectorDiscovery.java:90-103`
- Test: `connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/ConnectorConfigurationUtilTest.java`

**Interfaces:**
- Produces: `OutboundConnector.withLease()` → `boolean`, default `false`. `OutboundConnectorConfiguration.withLease()` → `boolean` (record accessor). These are consumed by Task 2.

- [ ] **Step 1: Write the failing tests**

In `connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/ConnectorConfigurationUtilTest.java`, add two `@Test` methods inside the `GetOutboundConnectorConfiguration` nested class (right after `shouldRetrieveConnectorConfiguration`):

```java
    @Test
    public void shouldRetrieveWithLeaseTrueFromAnnotation() {

      // when
      OutboundConnectorConfiguration configuration =
          ConnectorConfigurationUtil.getOutboundConnectorConfiguration(
              LeasedAnnotatedFunction.class);

      // then
      assertThat(configuration.withLease()).isTrue();
    }

    @Test
    public void shouldDefaultWithLeaseToFalseWhenNotSetOnAnnotation() {

      // when
      OutboundConnectorConfiguration configuration =
          ConnectorConfigurationUtil.getOutboundConnectorConfiguration(AnnotatedFunction.class);

      // then
      assertThat(configuration.withLease()).isFalse();
    }
```

Add a new top-level test-fixture class at the bottom of the file, next to `AnnotatedFunction`:

```java
@OutboundConnector(
    name = "LEASED",
    inputVariables = {"FOO"},
    type = "io.camunda.Leased",
    withLease = true)
class LeasedAnnotatedFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    return null;
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl connector-runtime/connector-runtime-core -am -Dtest=ConnectorConfigurationUtilTest`
Expected: COMPILE ERROR — `withLease` is not a valid attribute of `@OutboundConnector`, and `configuration.withLease()` does not exist.

- [ ] **Step 3: Add `withLease` to the `@OutboundConnector` annotation**

In `connector-sdk/core/src/main/java/io/camunda/connector/api/annotation/OutboundConnector.java`, add after the `type()` attribute:

```java
  /**
   * Whether to activate jobs for this connector with a lease, fencing complete/fail/throw-error
   * commands against a stale, superseded activation of the same job.
   */
  boolean withLease() default false;
```

- [ ] **Step 4: Add `withLease` to `OutboundConnectorConfiguration`**

Replace the full contents of `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/config/OutboundConnectorConfiguration.java` (from the `public record` declaration onward) with:

```java
public record OutboundConnectorConfiguration(
    String name,
    String[] inputVariables,
    String type,
    Supplier<OutboundConnectorFunction> instanceSupplier,
    @Nullable Long timeout,
    boolean withLease)
    implements ConnectorConfiguration {

  public OutboundConnectorConfiguration(
      String name,
      String[] inputVariables,
      String type,
      Supplier<OutboundConnectorFunction> instance) {
    this(name, inputVariables, type, instance, null, false);
  }

  public OutboundConnectorConfiguration(
      String name,
      String[] inputVariables,
      String type,
      Supplier<OutboundConnectorFunction> instance,
      @Nullable Long timeout) {
    this(name, inputVariables, type, instance, timeout, false);
  }

  @Override
  public ConnectorDirection direction() {
    return ConnectorDirection.OUTBOUND;
  }
}
```

The two secondary constructors keep every existing 4-arg and 5-arg call site across the codebase (there are ~15, mostly in tests like `OutboundConnectorsRestControllerTest`, `BaseOutboundMultiInstancesTest`, `OutboundConnectorsServiceTest`, `JobRetriesIntegrationTest`) compiling unchanged.

- [ ] **Step 5: Wire `withLease` through the three construction sites**

In `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/ConnectorConfigurationUtil.java`, replace lines 58-63:

```java
    return new OutboundConnectorConfiguration(
        annotation.name(),
        getInputVariables(cls, annotation),
        configurationOverrides.typeOverride().orElse(annotation.type()),
        () -> instantiateConnector(cls),
        configurationOverrides.timeoutOverride().orElse(null));
```

with:

```java
    return new OutboundConnectorConfiguration(
        annotation.name(),
        getInputVariables(cls, annotation),
        configurationOverrides.typeOverride().orElse(annotation.type()),
        () -> instantiateConnector(cls),
        configurationOverrides.timeoutOverride().orElse(null),
        annotation.withLease());
```

In `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/outbound/DefaultOutboundConnectorFactory.java`, replace lines 221-227:

```java
    return new OutboundConnectorConfiguration(
        outboundConnector.name(),
        outboundConnector.inputVariables(),
        configurationOverrides.typeOverride().orElse(outboundConnector.type()),
        instanceProvider,
        configurationOverrides.timeoutOverride().orElse(null));
```

with:

```java
    return new OutboundConnectorConfiguration(
        outboundConnector.name(),
        outboundConnector.inputVariables(),
        configurationOverrides.typeOverride().orElse(outboundConnector.type()),
        instanceProvider,
        configurationOverrides.timeoutOverride().orElse(null),
        outboundConnector.withLease());
```

In `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/discovery/EnvVarsConnectorDiscovery.java`, replace lines 90-103:

```java
      return new OutboundConnectorConfiguration(
          name,
          getConnectorEnvironmentVariable(name, "INPUT_VARIABLES")
              .map(variables -> variables.split(","))
              .or(() -> annotationConfig.map(OutboundConnectorConfiguration::inputVariables))
              .orElseThrow(() -> envMissing("Variables not specified", name, "INPUT_VARIABLES")),
          getConnectorEnvironmentVariable(name, "TYPE")
              .or(() -> annotationConfig.map(OutboundConnectorConfiguration::type))
              .orElseThrow(() -> envMissing("Type not specified", name, "TYPE")),
          () -> ConnectorConfigurationUtil.instantiateConnector(cls),
          getConnectorEnvironmentVariable(name, "TIMEOUT")
              .map(Long::parseLong)
              .or(() -> annotationConfig.map(OutboundConnectorConfiguration::timeout))
              .orElse(null));
```

with:

```java
      return new OutboundConnectorConfiguration(
          name,
          getConnectorEnvironmentVariable(name, "INPUT_VARIABLES")
              .map(variables -> variables.split(","))
              .or(() -> annotationConfig.map(OutboundConnectorConfiguration::inputVariables))
              .orElseThrow(() -> envMissing("Variables not specified", name, "INPUT_VARIABLES")),
          getConnectorEnvironmentVariable(name, "TYPE")
              .or(() -> annotationConfig.map(OutboundConnectorConfiguration::type))
              .orElseThrow(() -> envMissing("Type not specified", name, "TYPE")),
          () -> ConnectorConfigurationUtil.instantiateConnector(cls),
          getConnectorEnvironmentVariable(name, "TIMEOUT")
              .map(Long::parseLong)
              .or(() -> annotationConfig.map(OutboundConnectorConfiguration::timeout))
              .orElse(null),
          annotationConfig.map(OutboundConnectorConfiguration::withLease).orElse(false));
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn test -pl connector-runtime/connector-runtime-core -am -Dtest=ConnectorConfigurationUtilTest`
Expected: PASS (all tests in the class, including the two new ones)

- [ ] **Step 7: Run the full module test suite to check for regressions**

Run: `mvn test -pl connector-runtime/connector-runtime-core -am`
Expected: PASS (no test broken by the new record field / constructors)

- [ ] **Step 8: Commit**

```bash
git add connector-sdk/core/src/main/java/io/camunda/connector/api/annotation/OutboundConnector.java \
        connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/config/OutboundConnectorConfiguration.java \
        connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/ConnectorConfigurationUtil.java \
        connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/outbound/DefaultOutboundConnectorFactory.java \
        connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/discovery/EnvVarsConnectorDiscovery.java \
        connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/ConnectorConfigurationUtilTest.java
git commit -m "feat(connector-sdk): add withLease to @OutboundConnector"
```

---

### Task 2: Wire `withLease` into the job worker (`OutboundConnectorManager`)

**Files:**
- Modify: `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/outbound/lifecycle/OutboundConnectorManager.java:176-187`
- Test: `connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/outbound/lifecycle/OutboundConnectorManagerTest.java`

**Interfaces:**
- Consumes: `OutboundConnectorConfiguration.withLease()` → `boolean` (Task 1).
- Produces: nothing consumed by later tasks — this closes the loop from annotation to `JobWorkerValue`.

- [ ] **Step 1: Write the failing tests**

In `connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/outbound/lifecycle/OutboundConnectorManagerTest.java`, add these imports:

```java
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.SourceAware;
import io.camunda.client.jobhandling.ManagedJobWorker;
import org.mockito.ArgumentCaptor;
```

Replace the existing `connectorConfig` helper (lines 55-58):

```java
  private static OutboundConnectorConfiguration connectorConfig(
      String type, Supplier<OutboundConnectorFunction> instanceSupplier) {
    return new OutboundConnectorConfiguration(type, new String[0], type, instanceSupplier, null);
  }
```

with an overloaded pair:

```java
  private static OutboundConnectorConfiguration connectorConfig(
      String type, Supplier<OutboundConnectorFunction> instanceSupplier) {
    return connectorConfig(type, instanceSupplier, false);
  }

  private static OutboundConnectorConfiguration connectorConfig(
      String type, Supplier<OutboundConnectorFunction> instanceSupplier, boolean withLease) {
    return new OutboundConnectorConfiguration(
        type, new String[0], type, instanceSupplier, null, withLease);
  }
```

Add two new `@Test` methods (anywhere among the other `onStart_...` tests):

```java
  @Test
  void onStart_setsWithLeaseOnJobWorkerValue_whenConnectorOptsIn() {
    var jobWorkerManager = mock(JobWorkerManager.class);
    var connectorFactory = mock(OutboundConnectorFactory.class);
    when(connectorFactory.getActiveConfigurations())
        .thenReturn(
            List.of(
                connectorConfig("type-a", () -> mock(OutboundConnectorFunction.class), true)));
    var documentFactory = mock(DocumentFactory.class);
    var secretFilterFactory = mock(SecretFilterFactory.class);
    var manager =
        managerWith(
            jobWorkerManager,
            connectorFactory,
            Map.of("tenant-a", documentFactory),
            Map.of("tenant-a", secretFilterFactory));
    var client = clientWithPhysicalTenantId("tenant-a");

    manager.onStart(client, "engine-a");

    var jobWorkerCaptor = ArgumentCaptor.forClass(ManagedJobWorker.class);
    verify(jobWorkerManager).createJobWorker(any(), jobWorkerCaptor.capture(), any());
    JobWorkerValue jobWorkerValue = jobWorkerCaptor.getValue().jobWorkerValue();
    assertThat(jobWorkerValue.getWithLease().value()).isTrue();
  }

  @Test
  void onStart_leavesWithLeaseUnset_whenConnectorDoesNotOptIn() {
    var jobWorkerManager = mock(JobWorkerManager.class);
    var connectorFactory = mock(OutboundConnectorFactory.class);
    when(connectorFactory.getActiveConfigurations())
        .thenReturn(
            List.of(connectorConfig("type-a", () -> mock(OutboundConnectorFunction.class))));
    var documentFactory = mock(DocumentFactory.class);
    var secretFilterFactory = mock(SecretFilterFactory.class);
    var manager =
        managerWith(
            jobWorkerManager,
            connectorFactory,
            Map.of("tenant-a", documentFactory),
            Map.of("tenant-a", secretFilterFactory));
    var client = clientWithPhysicalTenantId("tenant-a");

    manager.onStart(client, "engine-a");

    var jobWorkerCaptor = ArgumentCaptor.forClass(ManagedJobWorker.class);
    verify(jobWorkerManager).createJobWorker(any(), jobWorkerCaptor.capture(), any());
    JobWorkerValue jobWorkerValue = jobWorkerCaptor.getValue().jobWorkerValue();
    assertThat(jobWorkerValue.getWithLease()).isInstanceOf(SourceAware.Empty.class);
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl connector-runtime/connector-runtime-spring -am -Dtest=OutboundConnectorManagerTest`
Expected: COMPILE ERROR (no 3-arg `connectorConfig` overload yet) — this covers both the helper signature and the new assertions in one shot.

- [ ] **Step 3: Set `withLease` on the `JobWorkerValue`**

In `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/outbound/lifecycle/OutboundConnectorManager.java`, in `openWorkerForOutboundConnector`, right after the existing `timeout` block (currently lines 185-187):

```java
    if (connector.timeout() != null) {
      jobWorkerValue.setTimeout(new FromAnnotation<>(Duration.ofMillis(connector.timeout())));
    }
```

add:

```java
    if (connector.withLease()) {
      jobWorkerValue.setWithLease(new FromAnnotation<>(true));
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl connector-runtime/connector-runtime-spring -am -Dtest=OutboundConnectorManagerTest`
Expected: PASS (all tests in the class, including the two new ones)

- [ ] **Step 5: Run the full module test suite to check for regressions**

Run: `mvn test -pl connector-runtime/connector-runtime-spring -am`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/outbound/lifecycle/OutboundConnectorManager.java \
        connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/outbound/lifecycle/OutboundConnectorManagerTest.java
git commit -m "feat(connector-runtime): set withLease on JobWorkerValue when a connector opts in"
```

---

### Task 3: `JobContext.getLeaseToken()` + production implementation

**Files:**
- Modify: `connector-sdk/core/src/main/java/io/camunda/connector/api/outbound/JobContext.java`
- Modify: `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/outbound/ActivatedJobContext.java`
- Test: `connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/outbound/JobHandlerContextTest.java`

**Interfaces:**
- Produces: `JobContext.getLeaseToken()` → `String`, nullable, default method returning `null`. Consumed by Task 4 (the test-support implementation overrides it).

- [ ] **Step 1: Write the failing tests**

In `connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/outbound/JobHandlerContextTest.java`, add two `@Test` methods (e.g. right after `getVariables`):

```java
  @Test
  void getLeaseToken() {
    when(activatedJob.getLeaseToken()).thenReturn("lease-token-1");
    assertThat(jobHandlerContext.getJobContext().getLeaseToken()).isEqualTo("lease-token-1");
  }

  @Test
  void getLeaseToken_nullWhenJobActivatedWithoutLease() {
    when(activatedJob.getLeaseToken()).thenReturn(null);
    assertThat(jobHandlerContext.getJobContext().getLeaseToken()).isNull();
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl connector-runtime/connector-runtime-core -am -Dtest=JobHandlerContextTest`
Expected: COMPILE ERROR — `ActivatedJob.getLeaseToken()` exists upstream already, but `JobContext.getLeaseToken()` does not yet exist, so `jobHandlerContext.getJobContext().getLeaseToken()` fails to compile.

- [ ] **Step 3: Add the default method to `JobContext`**

In `connector-sdk/core/src/main/java/io/camunda/connector/api/outbound/JobContext.java`, add before the closing brace of the interface (after `getTenantId()`):

```java

  /**
   * The lease token identifying this job's activation, or {@code null} if the job was activated
   * without a lease. Pass it along when making requests that should be fenced against a stale,
   * superseded activation of this same job.
   */
  default String getLeaseToken() {
    return null;
  }
```

- [ ] **Step 4: Implement it in `ActivatedJobContext`**

In `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/outbound/ActivatedJobContext.java`, add after `getTenantId()` (before the closing brace of the class):

```java

  @Override
  public String getLeaseToken() {
    return activatedJob.getLeaseToken();
  }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -pl connector-runtime/connector-runtime-core -am -Dtest=JobHandlerContextTest`
Expected: PASS (all tests in the class, including the two new ones)

- [ ] **Step 6: Run the full module test suite to check for regressions**

Run: `mvn test -pl connector-runtime/connector-runtime-core -am`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add connector-sdk/core/src/main/java/io/camunda/connector/api/outbound/JobContext.java \
        connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/outbound/ActivatedJobContext.java \
        connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/outbound/JobHandlerContextTest.java
git commit -m "feat(connector-sdk): surface the job lease token on JobContext"
```

---

### Task 4: `getLeaseToken()` on the test-support `TestJobContext`

**Files:**
- Modify: `connector-runtime/connector-runtime-test/src/main/java/io/camunda/connector/runtime/test/outbound/TestJobContext.java`
- Test: Create `connector-runtime/connector-runtime-test/src/test/java/io/camunda/connector/runtime/test/outbound/TestJobContextTest.java`

**Interfaces:**
- Consumes: `JobContext.getLeaseToken()` default method (Task 3) — this task overrides it.
- Produces: `TestJobContext.setLeaseToken(String)` — a public setter connector authors can use in unit tests (not consumed by any other task in this plan).

- [ ] **Step 1: Write the failing test**

Create `connector-runtime/connector-runtime-test/src/test/java/io/camunda/connector/runtime/test/outbound/TestJobContextTest.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.test.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TestJobContextTest {

  @Test
  void getLeaseToken_returnsNullByDefault() {
    var jobContext = new TestJobContext(Map::of, () -> "{}");

    assertThat(jobContext.getLeaseToken()).isNull();
  }

  @Test
  void getLeaseToken_returnsValueSetBySetter() {
    var jobContext = new TestJobContext(Map::of, () -> "{}");

    jobContext.setLeaseToken("lease-token-1");

    assertThat(jobContext.getLeaseToken()).isEqualTo("lease-token-1");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl connector-runtime/connector-runtime-test -am -Dtest=TestJobContextTest`
Expected: COMPILE ERROR — `TestJobContext.setLeaseToken(String)` does not exist yet.

- [ ] **Step 3: Add the field, getter override, and setter**

In `connector-runtime/connector-runtime-test/src/main/java/io/camunda/connector/runtime/test/outbound/TestJobContext.java`, add a field next to the other fields (after `private String tenantId;`):

```java
  private String leaseToken;
```

Add the getter/setter pair next to the other getter/setter pairs, e.g. after `setTenantId`:

```java

  @Override
  public String getLeaseToken() {
    return leaseToken;
  }

  public void setLeaseToken(String leaseToken) {
    this.leaseToken = leaseToken;
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl connector-runtime/connector-runtime-test -am -Dtest=TestJobContextTest`
Expected: PASS

- [ ] **Step 5: Run the full module test suite to check for regressions**

Run: `mvn test -pl connector-runtime/connector-runtime-test -am`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add connector-runtime/connector-runtime-test/src/main/java/io/camunda/connector/runtime/test/outbound/TestJobContext.java \
        connector-runtime/connector-runtime-test/src/test/java/io/camunda/connector/runtime/test/outbound/TestJobContextTest.java
git commit -m "feat(connector-runtime-test): support setting a lease token on TestJobContext"
```

---

### Task 5: Full build verification and spec cleanup

**Files:**
- Delete: `docs/superpowers/specs/2026-08-06-job-lease-sdk-design.md`
- Delete: `docs/superpowers/plans/2026-08-06-job-lease-sdk.md` (this file)

**Interfaces:** None — this is a whole-repo verification and cleanup pass, not a code change.

- [ ] **Step 1: Run the full connector-sdk and connector-runtime build**

Run: `mvn clean install -pl connector-sdk/core,connector-runtime/connector-runtime-core,connector-runtime/connector-runtime-spring,connector-runtime/connector-runtime-test,connector-runtime/spring-boot-starter-camunda-connectors -am -DskipITs`
Expected: `BUILD SUCCESS` — this rebuilds every module touched by Tasks 1-4 plus their downstream dependents (`spring-boot-starter-camunda-connectors`, which has its own `JobBuilder` test double referencing `JobClient` mocks), catching any missed call site.

- [ ] **Step 2: Spot-check for any other `OutboundConnectorConfiguration` construction sites broken by the record change**

Run: `grep -rn "new OutboundConnectorConfiguration(" --include="*.java" .`
Expected: every result is either a 4-arg call (matches the first secondary constructor), a 5-arg call (matches the second secondary constructor), or a 6-arg call (matches the canonical constructor, i.e. the three sites edited in Task 1). If Step 1's build passed, this is a confirmation pass, not a new discovery step.

- [ ] **Step 3: Delete the spec and plan documents**

Per project convention, spec/plan docs under `docs/superpowers/` are ephemeral working documents, not permanent project documentation.

```bash
git rm docs/superpowers/specs/2026-08-06-job-lease-sdk-design.md docs/superpowers/plans/2026-08-06-job-lease-sdk.md
git commit -m "chore: remove ephemeral spec/plan docs for job lease support"
```
