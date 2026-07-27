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
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.EvaluateExpressionCommandStep1.EvaluateExpressionCommandStep2;
import io.camunda.client.api.response.EvaluateExpressionResponse;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.inbound.ActivityLogTag;
import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.ElementTemplateDetails;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.feel.LocalFeelExpressionEvaluator;
import io.camunda.connector.runtime.core.FooBarSecretProvider;
import io.camunda.connector.runtime.core.TestObjectMapperSupplier;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImplTest.TestPropertiesClass.InnerObject;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InboundConnectorContextImplTest {
  private final SecretProvider secretProvider = new FooBarSecretProvider();
  private final ObjectMapper mapper = TestObjectMapperSupplier.INSTANCE;
  private final ActivityLogRegistry activityLogRegistry = new ActivityLogRegistry();

  /**
   * Default {@link CamundaClient} stub for tests that don't care about cluster-specific behavior:
   * it forwards each FEEL expression to a {@link LocalFeelExpressionEvaluator} so binding tests can
   * exercise the cluster code path without mocking each expression individually.
   */
  private final CamundaClient camundaClient = camundaClientBackedByLocalFeel();

  private static ValidInboundConnectorDetails getInboundConnectorDefinition(
      Map<String, String> properties) {
    return getInboundConnectorDefinitionWithTenant(properties, "<default>");
  }

  private static ValidInboundConnectorDetails getInboundConnectorDefinitionWithTenant(
      Map<String, String> properties, String tenantId) {
    properties = new HashMap<>(properties);
    properties.put("inbound.type", "io.camunda:connector:1");
    InboundConnectorElement element =
        new InboundConnectorElement(
            properties,
            new StandaloneMessageCorrelationPoint("", "", null, null),
            new ProcessElementWithRuntimeData("bool", 0, 0, "id", tenantId));
    var details = InboundConnectorDetails.of(element.deduplicationId(List.of()), List.of(element));
    assertThat(details).isInstanceOf(ValidInboundConnectorDetails.class);
    return (ValidInboundConnectorDetails) details;
  }

  /**
   * Builds a {@link CamundaClient} mock whose {@code newEvaluateExpressionCommand} chain resolves
   * each expression via a real {@link LocalFeelExpressionEvaluator}. Useful for tests that want to
   * verify end-to-end FEEL binding behavior through {@link
   * InboundConnectorContextImpl#bindProperties(Class)} without re-stubbing per expression.
   */
  private static CamundaClient camundaClientBackedByLocalFeel() {
    var local = new LocalFeelExpressionEvaluator();
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var step2 = mock(EvaluateExpressionCommandStep2.class, RETURNS_DEEP_STUBS);
    when(client.newEvaluateExpressionCommand().expression(any()))
        .thenAnswer(
            invocation -> {
              String expression = invocation.getArgument(0, String.class);
              var response = mock(EvaluateExpressionResponse.class);
              when(response.getResult()).thenAnswer(unused -> local.evaluate(expression));
              when(step2.send().join()).thenReturn(response);
              return step2;
            });
    return client;
  }

  /**
   * Builds a {@link CamundaClient} mock whose {@code newEvaluateExpressionCommand().expression(x)}
   * resolves to the value mapped from {@code x} in {@code expressionToResult}. Useful for verifying
   * that {@link InboundConnectorContextImpl#bindProperties(Class)} forwards each {@code @FEEL}
   * field as its own evaluation through the cluster.
   */
  private static CamundaClient mockClusterEvaluations(Map<String, Object> expressionToResult) {
    var camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var step2 = mock(EvaluateExpressionCommandStep2.class, RETURNS_DEEP_STUBS);
    when(camundaClient.newEvaluateExpressionCommand().expression(any()))
        .thenAnswer(
            invocation -> {
              String expression = invocation.getArgument(0, String.class);
              var response = mock(EvaluateExpressionResponse.class);
              when(response.getResult()).thenReturn(expressionToResult.get(expression));
              when(step2.send().join()).thenReturn(response);
              return step2;
            });
    return camundaClient;
  }

  @Test
  void bindProperties_fromActivatedElement_bindsThatElementsProperties() {
    // given a context whose correlation activates a specific element carrying its own raw
    // properties. Issue #6684: element-scoped binding must use the element that actually matched,
    // not the executable's shared/first element.
    var definition = getInboundConnectorDefinition(Map.of("stringMap", "={}"));
    var activatedElement =
        new ProcessElementWithRuntimeData(
            "bool",
            null,
            null,
            0,
            0,
            "activated",
            null,
            null,
            "<default>",
            ProcessElementWithRuntimeData.DEFAULT_PHYSICAL_TENANT_ID,
            new ElementTemplateDetails("t", "1", "icon"),
            Map.of("stringMap", "={\"from\":\"activated-element\"}"));
    var correlationHandler = mock(InboundCorrelationHandler.class);
    when(correlationHandler.correlate(any(), any()))
        .thenReturn(
            new CorrelationResult.Success.ProcessInstanceCreated(
                activatedElement, 1L, "<default>"));
    var context =
        new InboundConnectorContextImpl(
            secretProvider,
            (e) -> {},
            definition,
            correlationHandler,
            (e) -> {},
            mapper,
            activityLogRegistry,
            camundaClient);

    // when
    var result = context.correlate(CorrelationRequest.builder().variables(Map.of()).build());

    // then the Success binds the ACTIVATED element's own properties, not the context's
    assertThat(result).isInstanceOf(CorrelationResult.Success.class);
    var bound = ((CorrelationResult.Success) result).bindProperties(TestPropertiesClass.class);
    assertThat(bound.getStringMap()).containsEntry("from", "activated-element");
  }

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
            activityLogRegistry,
            camundaClient);
    // when and then
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> inboundConnectorContext.bindProperties(TestPropertiesClass.class));
    // Outer wrapper from bindProperties; the FEEL failure surfaces in the cause chain.
    assertThat(exception).hasMessageContaining("Failed to bind process instance properties");
    assertThat(exception).hasStackTraceContaining(FeelEngineWrapperException.class.getName());
    assertThat(exception).hasStackTraceContaining("Failed to evaluate expression");
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
            activityLogRegistry,
            camundaClient);
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
            activityLogRegistry,
            camundaClient);
    // when
    TestPropertiesClass propertiesAsType =
        inboundConnectorContext.bindProperties(TestPropertiesClass.class);
    // then
    assertThat(propertiesAsType.getMapWithStringListWithNumbers().get("key").getFirst())
        .isInstanceOf(String.class);
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
            activityLogRegistry,
            camundaClient);
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
            activityLogRegistry,
            camundaClient);

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
            secretProvider,
            (e) -> {},
            definition,
            null,
            (e) -> {},
            mapper,
            activityLogRegistry,
            camundaClient);

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
  void bindProperties_shouldForwardClusterVariableExpressionToCluster() {
    // given a property referencing a cluster-scoped variable (e.g. camunda.vars.env.*)
    var definition =
        getInboundConnectorDefinitionWithTenant(
            Map.of("str", "=camunda.vars.env.MY_API_KEY"), "tenant-1");
    var camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var step2 = mock(EvaluateExpressionCommandStep2.class, RETURNS_DEEP_STUBS);
    var response = mock(EvaluateExpressionResponse.class);
    var expressionCaptor = ArgumentCaptor.forClass(String.class);
    when(camundaClient.newEvaluateExpressionCommand().expression(expressionCaptor.capture()))
        .thenReturn(step2);
    when(step2.send().join()).thenReturn(response);
    when(response.getResult()).thenReturn("resolved-api-key");

    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider,
            (e) -> {},
            null,
            definition,
            null,
            (e) -> {},
            mapper,
            activityLogRegistry,
            camundaClient);

    // when
    TestPropertiesClass result = inboundConnectorContext.bindProperties(TestPropertiesClass.class);

    // then the cluster receives the expression verbatim and the result is bound
    assertThat(expressionCaptor.getValue()).contains("camunda.vars.env.MY_API_KEY");
    assertThat(result.str).isEqualTo("resolved-api-key");
    // tenant must be propagated so cluster variables can be resolved against the right scope
    verify(step2).tenantId(eq("tenant-1"));
  }

  @Test
  void bindProperties_shouldUseCamundaClientEvaluatorWithTenantId() {
    // given
    var definition =
        getInboundConnectorDefinitionWithTenant(Map.of("str", "= anything"), "tenant-1");
    var camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var step2 = mock(EvaluateExpressionCommandStep2.class, RETURNS_DEEP_STUBS);
    var response = mock(EvaluateExpressionResponse.class);
    when(camundaClient.newEvaluateExpressionCommand().expression(any())).thenReturn(step2);
    when(step2.send().join()).thenReturn(response);
    when(response.getResult()).thenReturn("evaluated-by-cluster");

    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider,
            (e) -> {},
            null,
            definition,
            null,
            (e) -> {},
            mapper,
            activityLogRegistry,
            camundaClient);

    // when
    TestPropertiesClass result = inboundConnectorContext.bindProperties(TestPropertiesClass.class);

    // then
    assertThat(result.str).isEqualTo("evaluated-by-cluster");
    verify(step2).tenantId(eq("tenant-1"));
    verify(step2, never()).scopeKey(org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void reportHealth_shouldLogErrorSeverityWhenStatusIsDown() {
    // given
    var definition = getInboundConnectorDefinition(Map.of());
    var health = Health.down();
    InboundConnectorContextImpl inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider,
            (e) -> {},
            definition,
            null,
            (e) -> {},
            mapper,
            activityLogRegistry,
            camundaClient);

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
  void bindProperties_shouldBindListFromClusterResult() {
    // given the cluster returns a List for a @FEEL List<String> field
    var definition =
        getInboundConnectorDefinition(Map.of("stringList", "=camunda.vars.env.RECIPIENTS"));
    var camundaClient =
        mockClusterEvaluations(
            Map.of("=camunda.vars.env.RECIPIENTS", List.of("alice", "bob", "carol")));

    var inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider,
            (e) -> {},
            null,
            definition,
            null,
            (e) -> {},
            mapper,
            activityLogRegistry,
            camundaClient);

    // when
    TestPropertiesClass result = inboundConnectorContext.bindProperties(TestPropertiesClass.class);

    // then
    assertThat(result.stringList).containsExactly("alice", "bob", "carol");
  }

  @Test
  void bindProperties_shouldBindMapFromClusterResult() {
    // given the cluster returns a Map for a @FEEL Map<String, String> field
    var definition =
        getInboundConnectorDefinition(Map.of("stringMap", "=camunda.vars.env.HEADERS"));
    var camundaClient =
        mockClusterEvaluations(
            Map.of(
                "=camunda.vars.env.HEADERS",
                Map.of("Authorization", "Bearer 123", "X-Tenant", "acme")));

    var inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider,
            (e) -> {},
            null,
            definition,
            null,
            (e) -> {},
            mapper,
            activityLogRegistry,
            camundaClient);

    // when
    TestPropertiesClass result = inboundConnectorContext.bindProperties(TestPropertiesClass.class);

    // then
    assertThat(result.stringMap)
        .containsEntry("Authorization", "Bearer 123")
        .containsEntry("X-Tenant", "acme");
  }

  @Test
  void bindProperties_shouldBindNullFromClusterResult() {
    // given the cluster returns null (e.g. optional cluster variable not set)
    var definition =
        getInboundConnectorDefinition(Map.of("str", "=camunda.vars.env.OPTIONAL_VALUE"));
    var expressionToResult = new HashMap<String, Object>();
    expressionToResult.put("=camunda.vars.env.OPTIONAL_VALUE", null);
    var camundaClient = mockClusterEvaluations(expressionToResult);

    var inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider,
            (e) -> {},
            null,
            definition,
            null,
            (e) -> {},
            mapper,
            activityLogRegistry,
            camundaClient);

    // when
    TestPropertiesClass result = inboundConnectorContext.bindProperties(TestPropertiesClass.class);

    // then
    assertThat(result.str).isNull();
  }

  @Test
  void bindProperties_shouldConvertIntegerResultsToLongList() {
    // given the cluster returns Integers but the target field is Map<String, List<Long>>
    var definition =
        getInboundConnectorDefinition(
            Map.of("mapWithNumberList", "=camunda.vars.env.RETRY_LIMITS"));
    var camundaClient =
        mockClusterEvaluations(
            Map.of("=camunda.vars.env.RETRY_LIMITS", Map.of("key", List.of(1, 2, 3))));

    var inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider,
            (e) -> {},
            null,
            definition,
            null,
            (e) -> {},
            mapper,
            activityLogRegistry,
            camundaClient);

    // when
    TestPropertiesClass result = inboundConnectorContext.bindProperties(TestPropertiesClass.class);

    // then Jackson widens the Integer elements to Long during binding
    assertThat(result.mapWithNumberList).containsEntry("key", List.of(1L, 2L, 3L));
  }

  @Test
  void bindProperties_shouldBindNestedObjectFromClusterResult() {
    // given the cluster returns a nested Map that should bind to a Map<String, InnerObject>
    var definition =
        getInboundConnectorDefinition(Map.of("stringObjectMap", "=camunda.vars.env.NESTED_CONFIG"));
    var camundaClient =
        mockClusterEvaluations(
            Map.of(
                "=camunda.vars.env.NESTED_CONFIG",
                Map.of("inner", Map.of("stringList", List.of("x", "y"), "bool", true))));

    var inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider,
            (e) -> {},
            null,
            definition,
            null,
            (e) -> {},
            mapper,
            activityLogRegistry,
            camundaClient);

    // when
    TestPropertiesClass result = inboundConnectorContext.bindProperties(TestPropertiesClass.class);

    // then the nested JSON-like structure is bound to the record-valued map entry
    assertThat(result.stringObjectMap)
        .containsEntry("inner", new InnerObject(List.of("x", "y"), true));
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
