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
package io.camunda.connector.runtime.core.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.inbound.ActivityLogTag;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.FooBarSecretProvider;
import io.camunda.connector.runtime.core.TestObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImplTest.TestPropertiesClass.InnerObject;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class InboundConnectorContextImplTest {
  private final SecretProvider secretProvider = new FooBarSecretProvider();
  private final ObjectMapper mapper = TestObjectMapperSupplier.INSTANCE;
  private final ActivityLogRegistry activityLogRegistry = new ActivityLogRegistry();

  @Test
  void bindProperties_shouldThrowExceptionWhenWrongFormat() {
    // given
    var definition = getInboundConnectorDefinition(Map.of("stringMap", "={{\"key\":\"value\"}"));
    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);
    // when and then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> inboundConnectorContext.bindProperties(TestPropertiesClass.class));
    assertThat(exception.getMessage()).contains("Failed to evaluate expression");
  }

  @Test
  void bindProperties_shouldParseNullValue() {
    // given
    var definition = getInboundConnectorDefinition(Map.of("stringMap", "={\"keyString\":null}"));
    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);
    // when
    TestPropertiesClass propertiesAsType =
        inboundConnectorContext.bindProperties(TestPropertiesClass.class);
    // then
    assertThat(propertiesAsType.getStringMap().containsKey("keyString")).isTrue();
    assertThat(propertiesAsType.getStringMap().get("keyString")).isNull();
  }

  @Test
  void bindProperties_shouldParseStringAsString() {
    // given
    var definition =
        getInboundConnectorDefinition(
            Map.of(
                "mapWithStringListWithNumbers",
                "={key:[\"34\", \"45\", \"890\",\"0\",\"16785\"]}"));
    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);
    // when
    TestPropertiesClass propertiesAsType =
        inboundConnectorContext.bindProperties(TestPropertiesClass.class);
    // then
    assertThat(propertiesAsType.getMapWithStringListWithNumbers().get("key").getFirst())
        .isInstanceOf(String.class);
  }

  // #7730: inbound secret resolution is restricted to the names the element's own deployed
  // zeebe:property text declares. The allow-list is a superset of what a correctly-authored model
  // names, so what it actually stops is a second-order lookup: replaceSecrets runs the brace pass
  // and then runs the bare pass over that pass's output, letting a resolved value that contains
  // reference-shaped text reach a secret no model ever declared. Confirmed present on this branch
  // before backporting: under the previous allow-all filter, a secret whose value is the literal
  // "secrets.CHAINED" resolved CHAINED.
  //
  // Main's suite also covers a hot swap, a sibling element's names, and the new
  // camunda.secrets.<name> form. None are representable on this branch: connectorDetails and
  // properties are both final and there is no updateConnectorDetails at all, there is a single
  // connector-level text rather than a per-element one, and the new form does not exist here.

  private InboundConnectorContextImpl filteringContext(
      ValidInboundConnectorDetails details, SecretProvider secretProvider) {
    return new InboundConnectorContextImpl(
        secretProvider,
        (e) -> {},
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE),
        details,
        null,
        (e) -> {},
        mapper,
        activityLogRegistry,
        true);
  }

  private static String replace(InboundConnectorContextImpl context, String input) {
    return context.getSecretHandler().replaceSecrets(input, new SecretContext("t"));
  }

  @Test
  void secretFilterEnabled_resolvesADeclaredSecret() {
    var secretProvider = mock(SecretProvider.class);
    when(secretProvider.getSecret(eq("DECLARED"), any())).thenReturn("resolved");
    var context =
        filteringContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.DECLARED")), secretProvider);

    assertThat(replace(context, "secrets.DECLARED")).isEqualTo("resolved");
  }

  @Test
  void secretFilterEnabled_resolvesADeclaredSecretInBraceSyntax() {
    var secretProvider = mock(SecretProvider.class);
    when(secretProvider.getSecret(eq("BRACED"), any())).thenReturn("resolved");
    var context =
        filteringContext(
            getInboundConnectorDefinition(Map.of("token", "{{ secrets.BRACED }}")), secretProvider);

    assertThat(replace(context, "{{ secrets.BRACED }}")).isEqualTo("resolved");
  }

  @Test
  void secretFilterEnabled_leavesAnUndeclaredSecret() {
    var secretProvider = mock(SecretProvider.class);
    var context =
        filteringContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.DECLARED")), secretProvider);

    assertThat(replace(context, "secrets.INJECTED")).isEqualTo("secrets.INJECTED");
    verify(secretProvider, never()).getSecret(eq("INJECTED"), any());
  }

  @Test
  void secretFilterEnabled_leavesASecretNamedOnlyByAnotherSecretsValue() {
    var secretProvider = mock(SecretProvider.class);
    when(secretProvider.getSecret(eq("CHAIN_ROOT"), any())).thenReturn("secrets.CHAINED");
    when(secretProvider.getSecret(eq("CHAINED"), any())).thenReturn("leaked-value");
    var context =
        filteringContext(
            getInboundConnectorDefinition(Map.of("token", "{{secrets.CHAIN_ROOT}}")),
            secretProvider);

    assertThat(replace(context, "{{secrets.CHAIN_ROOT}}")).isEqualTo("secrets.CHAINED");
    verify(secretProvider, never()).getSecret(eq("CHAINED"), any());
  }

  @Test
  void secretFilterDisabled_resolvesAnUndeclaredSecret() {
    var secretProvider = mock(SecretProvider.class);
    when(secretProvider.getSecret(eq("INJECTED"), any())).thenReturn("resolved");
    var context =
        new InboundConnectorContextImpl(
            secretProvider,
            (e) -> {},
            getInboundConnectorDefinition(Map.of("token", "secrets.DECLARED")),
            null,
            (e) -> {},
            mapper,
            activityLogRegistry);

    assertThat(replace(context, "secrets.INJECTED")).isEqualTo("resolved");
  }

  private static ValidInboundConnectorDetails getInboundConnectorDefinition(
      Map<String, String> properties) {
    properties = new HashMap<>(properties);
    properties.put("inbound.type", "io.camunda:connector:1");
    InboundConnectorElement element =
        new InboundConnectorElement(
            properties,
            new StandaloneMessageCorrelationPoint("", "", null, null),
            new ProcessElementWithRuntimeData("bool", 0, 0, "id", "<default>"));
    var details = InboundConnectorDetails.of(element.deduplicationId(List.of()), List.of(element));
    assertThat(details).isInstanceOf(ValidInboundConnectorDetails.class);
    return (ValidInboundConnectorDetails) details;
  }

  @Test
  void bindProperties_shouldParseAllObject() {
    // Given
    var definition =
        getInboundConnectorDefinition(
            Map.of(
                "stringMap",
                "={\"keyString\":\"valueString\"}",
                "stringMapMap",
                "={\"keyString\":{\"innerKeyString\":\"innerValueString\"}}",
                "stringList",
                "=[\"value1\", \"value2\", \"value3\"]",
                "numberList",
                "=[34, -45, 890, 0, -16785]",
                "str",
                "foo",
                "bool",
                "=true",
                "mapWithNumberList",
                "={\"key\":[43, 0, -123]}",
                "mapWithStringListWithNumbers",
                "={\"key\":[\"34\", \"45\", \"890\",\"0\",\"16785\"]}",
                "stringNumberList",
                "=[\"34\", \"-45\", \"890\", \"0\", \"-16785\"]",
                "stringObjectMap",
                "={\"innerObject\":{\"stringList\":[\"innerList\"], \"bool\":true}}"));
    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);
    // when
    TestPropertiesClass propertiesAsType =
        inboundConnectorContext.bindProperties(TestPropertiesClass.class);
    // then
    assertThat(propertiesAsType).isEqualTo(createTestClass());
  }

  @Test
  void getProperties_shouldNotParseFeel() {
    // given
    var definition = getInboundConnectorDefinition(Map.of("stringMap", "={\"keyString\":null}"));

    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);

    // when
    Map<String, Object> properties = inboundConnectorContext.getProperties();

    // then
    assertThat(properties.get("stringMap")).isEqualTo("={\"keyString\":null}");
  }

  @Test
  void reportHealth_shouldLogInfoSeverityWhenStatusIsUp() {
    // given
    var definition = getInboundConnectorDefinition(Map.of());
    var health = Health.up();
    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);

    // when
    inboundConnectorContext.reportHealth(health);

    // then
    var logs =
        activityLogRegistry.getLogs(ExecutableId.fromDeduplicationId(definition.deduplicationId()));
    assertThat(logs)
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.tag()).isEqualTo(ActivityLogTag.HEALTH);
              assertThat(log.healthChange()).isEqualTo(health);
              assertThat(log.severity()).isEqualTo(Severity.INFO);
            });
  }

  @Test
  void reportHealth_shouldLogErrorSeverityWhenStatusIsDown() {
    // given
    var definition = getInboundConnectorDefinition(Map.of());
    var health = Health.down();
    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);

    // when
    inboundConnectorContext.reportHealth(health);

    // then
    var logs =
        activityLogRegistry.getLogs(ExecutableId.fromDeduplicationId(definition.deduplicationId()));
    assertThat(logs)
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.tag()).isEqualTo(ActivityLogTag.HEALTH);
              assertThat(log.healthChange()).isEqualTo(health);
              assertThat(log.severity()).isEqualTo(Severity.ERROR);
            });
  }

  @Test
  void log_masksASecretResolvedForThisConnector() {
    // given a connector declaring a legacy secret this context can resolve
    var definition = getInboundConnectorDefinition(Map.of("token", "secrets.FOO"));
    var context =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);

    // when an activity's message happens to carry the resolved value
    context.log(activity -> activity.withSeverity(Severity.ERROR).withMessage("token was bar"));

    // then the logged message has the value masked
    var logs =
        activityLogRegistry.getLogs(ExecutableId.fromDeduplicationId(definition.deduplicationId()));
    assertThat(logs)
        .singleElement()
        .satisfies(log -> assertThat(log.message()).isEqualTo("token was ***"));
  }

  @Test
  void log_withholdsMessageWhenSecretValuesCannotBeFetched() {
    // given a provider that fails to resolve the declared secret
    var definition = getInboundConnectorDefinition(Map.of("token", "secrets.FOO"));
    var failingProvider = mock(SecretProvider.class);
    when(failingProvider.fetchAll(any(), any())).thenThrow(new RuntimeException("down"));
    var context =
        new InboundConnectorContextImpl(
            failingProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);

    // when
    context.log(activity -> activity.withSeverity(Severity.ERROR).withMessage("token was bar"));

    // then the raw message is withheld rather than published unmasked
    var logs =
        activityLogRegistry.getLogs(ExecutableId.fromDeduplicationId(definition.deduplicationId()));
    assertThat(logs)
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.message()).doesNotContain("bar");
              assertThat(log.message()).contains("withheld");
            });
  }

  @Test
  void reportHealth_masksASecretInTheErrorMessage() {
    // given
    var definition = getInboundConnectorDefinition(Map.of("token", "secrets.FOO"));
    var context =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);

    // when the reported health carries the resolved value in its error
    context.reportHealth(Health.down(new RuntimeException("token was bar")));

    // then it is masked both in the stored health and in the activity log
    assertThat(context.getHealth().getError().message()).doesNotContain("bar");
    var logs =
        activityLogRegistry.getLogs(ExecutableId.fromDeduplicationId(definition.deduplicationId()));
    assertThat(logs)
        .singleElement()
        .satisfies(
            log -> assertThat(log.healthChange().getError().message()).doesNotContain("bar"));
  }

  @Test
  void reportHealth_dedupesIdenticalHealthAfterMasking() {
    // given
    var definition = getInboundConnectorDefinition(Map.of("token", "secrets.FOO"));
    var context =
        new InboundConnectorContextImpl(
            secretProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);

    // when the same failure is reported twice
    context.reportHealth(Health.down(new RuntimeException("token was bar")));
    context.reportHealth(Health.down(new RuntimeException("token was bar")));

    // then the second report is deduped against the masked, not the raw, health
    var logs =
        activityLogRegistry.getLogs(ExecutableId.fromDeduplicationId(definition.deduplicationId()));
    assertThat(logs).hasSize(1);
  }

  @Test
  void reportHealth_doesNotDedupeTwoDifferentUnmaskableFailures() {
    // given a provider that is down, so every health report is withheld
    var definition = getInboundConnectorDefinition(Map.of("token", "secrets.FOO"));
    var downProvider = mock(SecretProvider.class);
    when(downProvider.fetchAll(any(), any())).thenThrow(new RuntimeException("down"));
    var context =
        new InboundConnectorContextImpl(
            downProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);

    // when two distinct failures are reported during the outage
    context.reportHealth(Health.down(new RuntimeException("kafka broker unreachable")));
    context.reportHealth(Health.down(new RuntimeException("deserialization failed")));

    // then both are logged rather than the second being deduped against the first
    var logs =
        activityLogRegistry.getLogs(ExecutableId.fromDeduplicationId(definition.deduplicationId()));
    assertThat(logs).hasSize(2);
  }

  @Test
  void log_masksASecretThatRotatedAfterItWasBound() {
    // given a provider that resolves FOO to one value at bind time and a different one on re-read
    var definition = getInboundConnectorDefinition(Map.of("token", "secrets.FOO"));
    var rotatingProvider = mock(SecretProvider.class);
    when(rotatingProvider.getSecret(eq("FOO"), any())).thenReturn("old-value");
    when(rotatingProvider.fetchAll(any(), any())).thenReturn(List.of("new-value"));
    var context =
        new InboundConnectorContextImpl(
            rotatingProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);

    // when the bound value is used, binding first so it is actually substituted and captured
    context.getProperties();
    context.log(
        activity -> activity.withSeverity(Severity.ERROR).withMessage("token was old-value"));

    // then the bound value is redacted even though a re-read would return a different one
    var logs =
        activityLogRegistry.getLogs(ExecutableId.fromDeduplicationId(definition.deduplicationId()));
    assertThat(logs).last().satisfies(log -> assertThat(log.message()).isEqualTo("token was ***"));
  }

  @Test
  void log_withholdsMessageWhenTheReReadComesBackShort() {
    // given a provider declaring two secrets but only resolving one of them on re-read
    var definition = getInboundConnectorDefinition(Map.of("a", "secrets.FOO", "b", "secrets.BAR"));
    var partialProvider = mock(SecretProvider.class);
    when(partialProvider.fetchAll(any(), any())).thenReturn(List.of("only-one-value"));
    var context =
        new InboundConnectorContextImpl(
            partialProvider, (e) -> {}, definition, null, (e) -> {}, mapper, activityLogRegistry);

    // when nothing was ever bound, so only the (incomplete) re-read is available
    context.log(activity -> activity.withSeverity(Severity.ERROR).withMessage("token was leaked"));

    // then the message is withheld rather than published with only one of the two values masked
    var logs =
        activityLogRegistry.getLogs(ExecutableId.fromDeduplicationId(definition.deduplicationId()));
    assertThat(logs)
        .singleElement()
        .satisfies(log -> assertThat(log.message()).contains("withheld"));
  }

  // Main also covers a secret declared only by a grouped sibling element. That gap does not exist
  // on this branch: every element in a group must carry the same properties for the group to be
  // valid at all, and there is no per-element binding that could resolve a name the representative
  // properties never declare.

  private TestPropertiesClass createTestClass() {
    TestPropertiesClass testClass = new TestPropertiesClass();
    testClass.setStringMap(Map.of("keyString", "valueString"));
    testClass.setStringMapMap(Map.of("keyString", Map.of("innerKeyString", "innerValueString")));
    testClass.setStringList(List.of("value1", "value2", "value3"));
    testClass.setNumberList(List.of(34, -45, 890, 0, -16785));
    testClass.setStringNumberList(List.of("34", "-45", "890", "0", "-16785"));
    testClass.setStr("foo");
    testClass.setBool(true);
    testClass.setMapWithNumberList(Map.of("key", List.of(43L, 0L, -123L)));
    var innerObject = new InnerObject(List.of("innerList"), true);
    testClass.setStringObjectMap(Map.of("innerObject", innerObject));
    testClass.setMapWithStringListWithNumbers(
        Map.of("key", List.of("34", "45", "890", "0", "16785")));
    return testClass;
  }

  public static class TestPropertiesClass {
    @FEEL private Map<String, String> stringMap;
    @FEEL private Map<String, Map<String, String>> stringMapMap;
    @FEEL private Map<String, InnerObject> stringObjectMap;
    @FEEL private List<String> stringList;
    @FEEL private List<Integer> numberList;
    @FEEL private List<String> stringNumberList;
    @FEEL private Map<String, List<Long>> mapWithNumberList;
    @FEEL private Map<String, List<String>> mapWithStringListWithNumbers;
    @FEEL private String str;
    @FEEL private boolean bool;

    public Map<String, String> getStringMap() {
      return stringMap;
    }

    public void setStringMap(final Map<String, String> stringMap) {
      this.stringMap = stringMap;
    }

    public void setStringMapMap(final Map<String, Map<String, String>> stringMapMap) {
      this.stringMapMap = stringMapMap;
    }

    public void setStringObjectMap(final Map<String, InnerObject> stringObjectMap) {
      this.stringObjectMap = stringObjectMap;
    }

    public void setStringList(final List<String> stringList) {
      this.stringList = stringList;
    }

    public void setNumberList(final List<Integer> numberList) {
      this.numberList = numberList;
    }

    public void setStringNumberList(final List<String> stringNumberList) {
      this.stringNumberList = stringNumberList;
    }

    public void setMapWithNumberList(final Map<String, List<Long>> mapWithNumberList) {
      this.mapWithNumberList = mapWithNumberList;
    }

    public Map<String, List<String>> getMapWithStringListWithNumbers() {
      return mapWithStringListWithNumbers;
    }

    public void setMapWithStringListWithNumbers(
        final Map<String, List<String>> mapWithStringListWithNumbers) {
      this.mapWithStringListWithNumbers = mapWithStringListWithNumbers;
    }

    public void setStr(final String str) {
      this.str = str;
    }

    public void setBool(final boolean bool) {
      this.bool = bool;
    }

    public record InnerObject(List<String> stringList, boolean bool) {}

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final TestPropertiesClass that = (TestPropertiesClass) o;
      return bool == that.bool
          && Objects.equals(stringMap, that.stringMap)
          && Objects.equals(stringMapMap, that.stringMapMap)
          && Objects.equals(stringObjectMap, that.stringObjectMap)
          && Objects.equals(stringList, that.stringList)
          && Objects.equals(numberList, that.numberList)
          && Objects.equals(stringNumberList, that.stringNumberList)
          && Objects.equals(mapWithNumberList, that.mapWithNumberList)
          && Objects.equals(mapWithStringListWithNumbers, that.mapWithStringListWithNumbers)
          && Objects.equals(str, that.str);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          stringMap,
          stringMapMap,
          stringObjectMap,
          stringList,
          numberList,
          stringNumberList,
          mapWithNumberList,
          mapWithStringListWithNumbers,
          str,
          bool);
    }

    @Override
    public String toString() {
      return "TestPropertiesClass{"
          + "stringMap="
          + stringMap
          + ", stringMapMap="
          + stringMapMap
          + ", stringObjectMap="
          + stringObjectMap
          + ", stringList="
          + stringList
          + ", numberList="
          + numberList
          + ", stringNumberList="
          + stringNumberList
          + ", mapWithNumberList="
          + mapWithNumberList
          + ", mapWithStringListWithNumbers="
          + mapWithStringListWithNumbers
          + ", str='"
          + str
          + "'"
          + ", bool="
          + bool
          + "}";
    }
  }
}
