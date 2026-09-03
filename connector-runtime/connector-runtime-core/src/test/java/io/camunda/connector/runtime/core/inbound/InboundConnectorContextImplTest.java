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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.runtime.core.FooBarSecretProvider;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImplTest.TestPropertiesClass.InnerObject;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class InboundConnectorContextImplTest {
  private final SecretProvider secretProvider = new FooBarSecretProvider();
  private final ObjectMapper mapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  @Test
  void bindProperties_shouldThrowExceptionWhenWrongFormat() {
    // given
    var definition = getInboundConnectorDefinition(Map.of("stringMap", "={{\"key\":\"value\"}"));
    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider,
            (e) -> {},
            definition,
            null,
            (e) -> {},
            mapper,
            EvictingQueue.create(10));
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
            secretProvider,
            (e) -> {},
            definition,
            null,
            (e) -> {},
            mapper,
            EvictingQueue.create(10));
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
            secretProvider,
            (e) -> {},
            definition,
            null,
            (e) -> {},
            mapper,
            EvictingQueue.create(10));
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
      ValidInboundConnectorDetails details, SecretProvider provider) {
    return new InboundConnectorContextImpl(
        provider, (e) -> {}, details, null, (e) -> {}, mapper, EvictingQueue.create(10), true);
  }

  private static String replace(InboundConnectorContextImpl context, String input) {
    return context.getSecretHandler().replaceSecrets(input);
  }

  @Test
  void secretFilterEnabled_resolvesADeclaredSecret() {
    var provider = mock(SecretProvider.class);
    when(provider.getSecret("DECLARED")).thenReturn("resolved");
    var context =
        filteringContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.DECLARED")), provider);

    assertThat(replace(context, "secrets.DECLARED")).isEqualTo("resolved");
  }

  @Test
  void secretFilterEnabled_resolvesADeclaredSecretInBraceSyntax() {
    var provider = mock(SecretProvider.class);
    when(provider.getSecret("BRACED")).thenReturn("resolved");
    var context =
        filteringContext(
            getInboundConnectorDefinition(Map.of("token", "{{ secrets.BRACED }}")), provider);

    assertThat(replace(context, "{{ secrets.BRACED }}")).isEqualTo("resolved");
  }

  @Test
  void secretFilterEnabled_leavesAnUndeclaredSecret() {
    var provider = mock(SecretProvider.class);
    var context =
        filteringContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.DECLARED")), provider);

    assertThat(replace(context, "secrets.INJECTED")).isEqualTo("secrets.INJECTED");
    verify(provider, never()).getSecret("INJECTED");
  }

  @Test
  void secretFilterEnabled_leavesASecretNamedOnlyByAnotherSecretsValue() {
    var provider = mock(SecretProvider.class);
    when(provider.getSecret("CHAIN_ROOT")).thenReturn("secrets.CHAINED");
    when(provider.getSecret("CHAINED")).thenReturn("leaked-value");
    var context =
        filteringContext(
            getInboundConnectorDefinition(Map.of("token", "{{secrets.CHAIN_ROOT}}")), provider);

    assertThat(replace(context, "{{secrets.CHAIN_ROOT}}")).isEqualTo("secrets.CHAINED");
    verify(provider, never()).getSecret("CHAINED");
  }

  @Test
  void secretFilterDisabled_resolvesAnUndeclaredSecret() {
    var provider = mock(SecretProvider.class);
    when(provider.getSecret("INJECTED")).thenReturn("resolved");
    var context =
        new InboundConnectorContextImpl(
            provider,
            (e) -> {},
            getInboundConnectorDefinition(Map.of("token", "secrets.DECLARED")),
            null,
            (e) -> {},
            mapper,
            EvictingQueue.create(10));

    assertThat(replace(context, "secrets.INJECTED")).isEqualTo("resolved");
  }

  // Secret redaction of operator-visible output (#8643). The values are read back from the
  // provider rather than remembered from binding, and unioned with what this context actually
  // substituted, so a secret that rotated in between is still redacted by the value the message
  // carries.
  //
  // Main additionally covers a sibling element declaring a secret the representative element does
  // not, and a redeploy invalidating the cached re-read. Neither is representable here: grouped
  // elements on this branch must carry identical connector-level properties -- {@code
  // InboundConnectorDetailsUtil} rejects a group whose elements diverge -- and connectorDetails is
  // final with no updateConnectorDetails to invalidate anything.

  private InboundConnectorContextImpl loggingContext(
      ValidInboundConnectorDetails details, SecretProvider provider, EvictingQueue<Activity> logs) {
    return new InboundConnectorContextImpl(
        provider, (e) -> {}, details, null, (e) -> {}, mapper, logs);
  }

  private static Activity errorActivity(String message) {
    return Activity.level(Severity.ERROR).tag("error").message(message);
  }

  @Test
  void log_masksASecretResolvedForThisConnector() {
    // given a connector declaring a secret this context can resolve
    EvictingQueue<Activity> logs = EvictingQueue.create(10);
    var context =
        loggingContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.FOO")), secretProvider, logs);

    // when an activity's message happens to carry the resolved value
    context.log(errorActivity("token was " + FooBarSecretProvider.SECRET_VALUE));

    // then the logged message has the value masked
    assertThat(logs)
        .singleElement()
        .satisfies(log -> assertThat(log.message()).isEqualTo("token was ***"));
  }

  @Test
  void log_withholdsMessageWhenSecretValuesCannotBeFetched() {
    // given a provider that fails to resolve the declared secret
    EvictingQueue<Activity> logs = EvictingQueue.create(10);
    var failingProvider = mock(SecretProvider.class);
    when(failingProvider.getSecret("FOO")).thenThrow(new RuntimeException("secret store is down"));
    var context =
        loggingContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.FOO")), failingProvider, logs);

    // when
    context.log(errorActivity("token was bar"));

    // then the raw message is withheld rather than published unmasked, and the provider's own
    // message -- which can echo the secret store's response -- is not what replaces it
    assertThat(logs)
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.message())
                  .doesNotContain("bar")
                  .doesNotContain("secret store is down");
              assertThat(log.message()).contains("withheld");
            });
  }

  @Test
  void log_withholdsMessageWhenTheReReadComesBackShort() {
    // given a connector declaring two secrets of which the provider only has one
    EvictingQueue<Activity> logs = EvictingQueue.create(10);
    var partialProvider = mock(SecretProvider.class);
    when(partialProvider.getSecret("FOO")).thenReturn("only-one-value");
    var context =
        loggingContext(
            getInboundConnectorDefinition(Map.of("a", "secrets.FOO", "b", "secrets.BAR")),
            partialProvider,
            logs);

    // when nothing was ever bound, so only the incomplete re-read is available
    context.log(errorActivity("token was leaked"));

    // then the message is withheld rather than published with only one of the two values masked
    assertThat(logs)
        .singleElement()
        .satisfies(log -> assertThat(log.message()).contains("withheld"));
  }

  @Test
  void log_masksASecretThatRotatedAfterItWasBound() {
    // given a provider that resolves FOO to one value at bind time and another on re-read
    EvictingQueue<Activity> logs = EvictingQueue.create(10);
    var rotatingProvider = mock(SecretProvider.class);
    when(rotatingProvider.getSecret("FOO")).thenReturn("old-value", "new-value");
    var context =
        loggingContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.FOO")), rotatingProvider, logs);

    // when the bound value is used, binding first so it is actually substituted and captured
    context.getProperties();
    context.log(errorActivity("token was old-value"));

    // then the bound value is redacted even though a re-read returns a different one
    assertThat(logs)
        .singleElement()
        .satisfies(log -> assertThat(log.message()).isEqualTo("token was ***"));
  }

  @Test
  void log_readsTheSecretValuesOnceAndReusesThem() {
    // given
    EvictingQueue<Activity> logs = EvictingQueue.create(10);
    var provider = mock(SecretProvider.class);
    when(provider.getSecret("FOO")).thenReturn("bar");
    var context =
        loggingContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.FOO")), provider, logs);

    // when several messages are logged
    context.log(errorActivity("first was bar"));
    context.log(errorActivity("second was bar"));

    // then the provider is asked once: a per-message read would put the log path's cost, and a
    // transient failure's blast radius, on every message
    verify(provider, times(1)).getSecret("FOO");
    assertThat(logs).allSatisfy(log -> assertThat(log.message()).endsWith("was ***"));
  }

  @Test
  void log_masksASecretForAGroupedExecutable() {
    // given two elements deduplicated into one executable
    EvictingQueue<Activity> logs = EvictingQueue.create(10);
    var context =
        loggingContext(
            groupedDefinition(Map.of("token", "secrets.FOO"), "first", "second"),
            secretProvider,
            logs);

    // when
    context.log(errorActivity("token was " + FooBarSecretProvider.SECRET_VALUE));

    // then the scan across every grouped element still finds the declared name
    assertThat(logs)
        .singleElement()
        .satisfies(log -> assertThat(log.message()).isEqualTo("token was ***"));
  }

  @Test
  void log_isUnchangedWhenTheConnectorDeclaresNoSecret() {
    // given a connector that names no secret, so no provider is asked for anything
    EvictingQueue<Activity> logs = EvictingQueue.create(10);
    var provider = mock(SecretProvider.class);
    var context =
        loggingContext(getInboundConnectorDefinition(Map.of("token", "plain")), provider, logs);

    // when
    context.log(errorActivity("nothing secret here"));

    // then
    assertThat(logs)
        .singleElement()
        .satisfies(log -> assertThat(log.message()).isEqualTo("nothing secret here"));
    verify(provider, never()).getSecret(any());
  }

  @Test
  void reportHealth_masksASecretInTheError() {
    // given
    var context =
        loggingContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.FOO")),
            secretProvider,
            EvictingQueue.create(10));

    // when the reported health carries the resolved value in its error
    context.reportHealth(
        Health.down(new RuntimeException("token was " + FooBarSecretProvider.SECRET_VALUE)));

    // then it is masked in the stored health
    assertThat(context.getHealth().getError().message())
        .doesNotContain(FooBarSecretProvider.SECRET_VALUE);
  }

  @Test
  void reportHealth_masksTheErrorCodeToo() {
    // given
    var context =
        loggingContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.FOO")),
            secretProvider,
            EvictingQueue.create(10));

    // when the value reaches the code rather than the message; unlike a BPMN error code, nothing
    // routes on this one, so there is no reason to leave it unmasked
    var error = new Health.Error(FooBarSecretProvider.SECRET_VALUE, "failed");
    context.reportHealth(Health.down(error));

    // then
    assertThat(context.getHealth().getError().code()).isEqualTo("***");
  }

  @Test
  void reportHealth_withholdsTheErrorWhenSecretValuesCannotBeFetched() {
    // given
    var failingProvider = mock(SecretProvider.class);
    when(failingProvider.getSecret("FOO")).thenThrow(new RuntimeException("secret store is down"));
    var context =
        loggingContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.FOO")),
            failingProvider,
            EvictingQueue.create(10));

    // when
    context.reportHealth(Health.down(new RuntimeException("token was bar")));

    // then
    assertThat(context.getHealth().getError().message()).doesNotContain("bar").contains("withheld");
  }

  @Test
  void reportHealth_leavesAHealthWithoutAnErrorAlone() {
    // given
    var provider = mock(SecretProvider.class);
    var context =
        loggingContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.FOO")),
            provider,
            EvictingQueue.create(10));

    // when
    context.reportHealth(Health.up());

    // then no read is attempted for a health that carries no message to redact
    assertThat(context.getHealth()).isEqualTo(Health.up());
    verify(provider, never()).getSecret(any());
  }

  @NotNull
  private static ValidInboundConnectorDetails groupedDefinition(
      Map<String, String> properties, String... elementIds) {
    properties = new HashMap<>(properties);
    properties.put("inbound.type", "io.camunda:connector:1");
    var rawProperties = Map.copyOf(properties);
    var elements =
        Arrays.stream(elementIds)
            .map(
                elementId ->
                    new InboundConnectorElement(
                        rawProperties,
                        new StandaloneMessageCorrelationPoint("", "", null, null),
                        new ProcessElement("bool", 0, 0, elementId, "<default>")))
            .toList();
    var details =
        InboundConnectorDetails.of(elements.getFirst().deduplicationId(List.of()), elements);
    assertThat(details).isInstanceOf(ValidInboundConnectorDetails.class);
    return (ValidInboundConnectorDetails) details;
  }

  @NotNull
  private static ValidInboundConnectorDetails getInboundConnectorDefinition(
      Map<String, String> properties) {
    properties = new HashMap<>(properties);
    properties.put("inbound.type", "io.camunda:connector:1");
    InboundConnectorElement element =
        new InboundConnectorElement(
            properties,
            new StandaloneMessageCorrelationPoint("", "", null, null),
            new ProcessElement("bool", 0, 0, "id", "<default>"));
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
            secretProvider,
            (e) -> {},
            definition,
            null,
            (e) -> {},
            mapper,
            EvictingQueue.create(10));
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
            secretProvider,
            (e) -> {},
            definition,
            null,
            (e) -> {},
            mapper,
            EvictingQueue.create(10));

    // when
    Map<String, Object> properties = inboundConnectorContext.getProperties();

    // then
    assertThat(properties.get("stringMap")).isEqualTo("={\"keyString\":null}");
  }

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
