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
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.EvaluateExpressionCommandStep1.EvaluateExpressionCommandStep2;
import io.camunda.client.api.response.EvaluateExpressionResponse;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.ElementTemplateDetails;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.LocalFeelExpressionEvaluator;
import io.camunda.connector.runtime.core.TestObjectMapperSupplier;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import io.camunda.connector.runtime.core.secret.SecretFilterMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Covers the field-path-scoped {@link io.camunda.connector.runtime.core.secret.SecretFilter} that
 * {@link InboundConnectorContextImpl} builds for itself (#7730), mirroring the outbound mechanism
 * but sourced synchronously from {@code rawProperties} already in memory rather than a BPMN
 * re-fetch.
 */
class InboundConnectorContextImplSecretFilterTest {

  private final ObjectMapper mapper = TestObjectMapperSupplier.INSTANCE;
  private final ActivityLogRegistry activityLogRegistry = new ActivityLogRegistry();
  private final ValidationProvider validationProvider = ignored -> {};
  private final CamundaClient camundaClient = camundaClientBackedByLocalFeel();

  /**
   * Builds a {@link CamundaClient} mock whose {@code newEvaluateExpressionCommand} chain forwards
   * each expression to a real {@link LocalFeelExpressionEvaluator}, so property binding through
   * {@link InboundConnectorContextImpl#bindProperties(Class)} works end-to-end without stubbing
   * FEEL evaluation per expression.
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

  // ---------------------------------------------------------------------------------------
  // extractSecrets: the pure allow-list extraction the filter is built from
  // ---------------------------------------------------------------------------------------

  @Test
  void extractSecrets_findsBothLegacySpellingsAndSplitsTheKeyIntoAFieldPath() {
    var rawProperties = Map.of("auth.token", "{{secrets.FOO}}", "auth.bare", "secrets.BAR");

    var secrets = InboundConnectorContextImpl.extractSecrets(List.of(rawProperties));

    assertThat(secrets)
        .containsExactlyInAnyOrder(
            new Secret("FOO", List.of("auth", "token")),
            new Secret("BAR", List.of("auth", "bare")));
  }

  @Test
  void extractSecrets_findsMultipleSecretsDeclaredInOneValue() {
    var rawProperties = Map.of("header", "Bearer {{secrets.TOKEN}} for {{secrets.CLIENT_ID}}");

    var secrets = InboundConnectorContextImpl.extractSecrets(List.of(rawProperties));

    assertThat(secrets)
        .containsExactlyInAnyOrder(
            new Secret("TOKEN", List.of("header")), new Secret("CLIENT_ID", List.of("header")));
  }

  @Test
  void extractSecrets_returnsEmptyWhenNoPropertyDeclaresASecret() {
    var rawProperties = Map.of("plain", "just some text", "number", "42");

    var secrets = InboundConnectorContextImpl.extractSecrets(List.of(rawProperties));

    assertThat(secrets).isEmpty();
  }

  @Test
  void extractSecrets_unionsAcrossEveryElementRatherThanJustTheFirst() {
    // The allow-list has to cover whichever element bindElementProperties is later asked to
    // resolve secrets against, not just whichever element happened to seed the aggregate
    // rawPropertiesWithoutKeywords.
    var elementA = Map.of("a", "{{secrets.PRIMARY}}");
    var elementB = Map.of("b", "{{secrets.SECONDARY}}");

    var secrets = InboundConnectorContextImpl.extractSecrets(List.of(elementA, elementB));

    assertThat(secrets)
        .containsExactlyInAnyOrder(
            new Secret("PRIMARY", List.of("a")), new Secret("SECONDARY", List.of("b")));
  }

  @Test
  void extractSecrets_dedupesTheSameSecretAtTheSamePathAcrossElements() {
    var elementA = Map.of("token", "{{secrets.SHARED}}");
    var elementB = Map.of("token", "{{secrets.SHARED}}");

    var secrets = InboundConnectorContextImpl.extractSecrets(List.of(elementA, elementB));

    assertThat(secrets).containsExactly(new Secret("SHARED", List.of("token")));
  }

  // ---------------------------------------------------------------------------------------
  // Construction + resolution: SecretFilterMode actually gates what SecretHandler will resolve
  // ---------------------------------------------------------------------------------------

  @Test
  void strictMode_resolvesASecretTheConnectorsOwnPropertiesDeclare() {
    var secretProvider = mock(SecretProvider.class);
    when(secretProvider.getSecret(eq("FOO"), any())).thenReturn("bar-value");
    var context =
        contextWithProperties(
            Map.of("stringMap", "={\"token\": \"secrets.FOO\"}"),
            secretProvider,
            SecretFilterMode.STRICT);

    var bound = context.bindProperties(TestPropertiesClass.class);

    assertThat(bound.getStringMap()).containsEntry("token", "bar-value");
  }

  @Test
  void strictMode_leavesAnUndeclaredSecretReferenceUnresolved() {
    // Nothing in this connector's properties declares "secrets.UNDECLARED", so even though the
    // secret provider could resolve it, STRICT must refuse and leave the placeholder text as-is
    // rather than silently substituting a value the model never declared.
    var secretProvider = mock(SecretProvider.class);
    when(secretProvider.getSecret(eq("UNDECLARED"), any())).thenReturn("should-not-appear");
    var context =
        contextWithProperties(Map.of("stringMap", "={}"), secretProvider, SecretFilterMode.STRICT);
    var probe = probeNode("secrets.UNDECLARED");

    var result = context.getSecretHandler().replaceSecrets(probe, new SecretContext("t", "p"));

    assertThat(result.get("value").asText()).isEqualTo("secrets.UNDECLARED");
  }

  @Test
  void disabledMode_resolvesEvenASecretTheConnectorsOwnPropertiesNeverDeclared() {
    var secretProvider = mock(SecretProvider.class);
    when(secretProvider.getSecret(eq("UNDECLARED"), any())).thenReturn("resolved-anyway");
    var context =
        contextWithProperties(
            Map.of("stringMap", "={}"), secretProvider, SecretFilterMode.DISABLED);
    var probe = probeNode("secrets.UNDECLARED");

    var result = context.getSecretHandler().replaceSecrets(probe, new SecretContext("t", "p"));

    assertThat(result.get("value").asText()).isEqualTo("resolved-anyway");
  }

  @Test
  void strictMode_allowsASecretDeclaredOnlyOnASiblingElementOfTheSameConnector() {
    // this.properties (and rawPropertiesWithoutKeywords) is seeded from the FIRST element only,
    // which here declares nothing. The filter still has to allow SECONDARY because it is unioned
    // across every element connectorDetails carries, and correlation can activate any of them.
    var primaryElement = inboundElement("primary", Map.of("stringMap", "={}"));
    var siblingProperties = Map.of("stringMap", "={\"token\": \"secrets.SECONDARY\"}");
    var siblingElement = inboundElement("sibling", siblingProperties);
    var details =
        (ValidInboundConnectorDetails)
            InboundConnectorDetails.of(
                "test-dedup-id", List.of(primaryElement, siblingElement), List.of("inbound.type"));
    assertThat(details.rawPropertiesWithoutKeywords()).doesNotContainKey("token");

    var secretProvider = mock(SecretProvider.class);
    when(secretProvider.getSecret(eq("SECONDARY"), any())).thenReturn("sibling-value");
    var correlationHandler = mock(InboundCorrelationHandler.class);
    var activatedSibling =
        new ProcessElementWithRuntimeData(
            "bool",
            null,
            null,
            0,
            0,
            "sibling",
            null,
            null,
            "<default>",
            ProcessElementWithRuntimeData.DEFAULT_PHYSICAL_TENANT_ID,
            new ElementTemplateDetails("t", "1", "icon"),
            siblingProperties);
    when(correlationHandler.correlate(any(), any()))
        .thenReturn(
            new CorrelationResult.Success.ProcessInstanceCreated(
                activatedSibling, 1L, "<default>"));
    var context =
        new InboundConnectorContextImpl(
            secretProvider,
            validationProvider,
            details,
            correlationHandler,
            (e) -> {},
            mapper,
            activityLogRegistry,
            camundaClient,
            SecretFilterMode.STRICT);

    var result = context.correlate(CorrelationRequest.builder().variables(Map.of()).build());

    assertThat(result).isInstanceOf(CorrelationResult.Success.class);
    var bound = ((CorrelationResult.Success) result).bindProperties(TestPropertiesClass.class);
    assertThat(bound.getStringMap()).containsEntry("token", "sibling-value");
  }

  // ---------------------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------------------

  private InboundConnectorContextImpl contextWithProperties(
      Map<String, String> properties, SecretProvider secretProvider, SecretFilterMode mode) {
    var details =
        (ValidInboundConnectorDetails)
            InboundConnectorDetails.of(
                "test-dedup-id", List.of(inboundElement("only", properties)));
    return new InboundConnectorContextImpl(
        secretProvider,
        validationProvider,
        details,
        mock(InboundCorrelationHandler.class),
        (e) -> {},
        mapper,
        activityLogRegistry,
        camundaClient,
        mode);
  }

  private static InboundConnectorElement inboundElement(
      String elementId, Map<String, String> properties) {
    var withType = new java.util.HashMap<>(properties);
    withType.put("inbound.type", "io.camunda:connector:1");
    return new InboundConnectorElement(
        withType,
        new StandaloneMessageCorrelationPoint("", "", null, null),
        new ProcessElementWithRuntimeData("bool", 0, 0, elementId, "<default>"));
  }

  private ObjectNode probeNode(String value) {
    return mapper.createObjectNode().put("value", value);
  }

  public static class TestPropertiesClass {
    @FEEL private Map<String, String> stringMap;

    public Map<String, String> getStringMap() {
      return stringMap;
    }

    public void setStringMap(Map<String, String> stringMap) {
      this.stringMap = stringMap;
    }
  }
}
