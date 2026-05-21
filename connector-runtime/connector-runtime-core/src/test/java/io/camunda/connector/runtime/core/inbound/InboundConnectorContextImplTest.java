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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.EvaluateExpressionCommandStep1.EvaluateExpressionCommandStep2;
import io.camunda.client.api.response.EvaluateExpressionResponse;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.inbound.ActivityLogTag;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.FooBarSecretProvider;
import io.camunda.connector.runtime.core.TestObjectMapperSupplier;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InboundConnectorContextImplTest {
  private final SecretProvider secretProvider = new FooBarSecretProvider();
  private final ObjectMapper mapper = TestObjectMapperSupplier.INSTANCE;
  private final ActivityLogRegistry activityLogRegistry = new ActivityLogRegistry();
  private final CamundaClient camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);

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
  void bindProperties_shouldEvaluateMultipleFeelFieldsViaCluster() {
    // given several @FEEL fields, each referencing distinct cluster/tenant variables
    var definition =
        getInboundConnectorDefinitionWithTenant(
            Map.of(
                "str", "=camunda.vars.env.API_KEY",
                "second", "=camunda.vars.tenant.greeting"),
            "tenant-acme");
    var camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var step2 = mock(EvaluateExpressionCommandStep2.class, RETURNS_DEEP_STUBS);
    var expressionCaptor = ArgumentCaptor.forClass(String.class);
    when(camundaClient.newEvaluateExpressionCommand().expression(expressionCaptor.capture()))
        .thenReturn(step2);
    when(step2.send().join())
        .thenAnswer(
            invocation -> {
              var lastExpression =
                  expressionCaptor.getAllValues().get(expressionCaptor.getAllValues().size() - 1);
              var response = mock(EvaluateExpressionResponse.class);
              if (lastExpression.contains("API_KEY")) {
                when(response.getResult()).thenReturn("resolved-api-key");
              } else if (lastExpression.contains("greeting")) {
                when(response.getResult()).thenReturn("hello-acme");
              }
              return response;
            });

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

    // then both expressions were forwarded verbatim to the cluster
    assertThat(expressionCaptor.getAllValues())
        .containsExactlyInAnyOrder("=camunda.vars.env.API_KEY", "=camunda.vars.tenant.greeting");
    // both results are bound to their respective fields
    assertThat(result.str).isEqualTo("resolved-api-key");
    assertThat(result.second).isEqualTo("hello-acme");
    // tenant is propagated to every evaluation so tenant-scoped variables resolve correctly
    verify(step2, org.mockito.Mockito.times(2)).tenantId(eq("tenant-acme"));
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

  public static class TestPropertiesClass {
    @FEEL private String str;
    @FEEL private String second;

    public void setStr(final String str) {
      this.str = str;
    }

    public void setSecond(final String second) {
      this.second = second;
    }
  }
}
