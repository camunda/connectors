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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.runtime.core.FooBarSecretProvider;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImplTest.TestPropertiesClass.InnerObject;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class InboundConnectorContextImplTest {
  private final SecretProvider secretProvider = new FooBarSecretProvider();
  private final ObjectMapper mapper = ConnectorsObjectMapperSupplier.getCopy();

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

  private InboundConnectorContextImpl filteringContext(
      ValidInboundConnectorDetails details, SecretProvider provider) {
    return new InboundConnectorContextImpl(
        provider,
        (e) -> {},
        null,
        details,
        null,
        (e) -> {},
        mapper,
        EvictingQueue.create(10),
        true);
  }

  private static String replace(InboundConnectorContextImpl context, String field, String input) {
    ObjectNode probe = new ObjectMapper().createObjectNode().put(field, input);
    return context
        .getSecretHandler()
        .replaceSecrets(probe, new SecretContext("t"))
        .get(field)
        .asText();
  }

  @Test
  void secretFilterEnabled_resolvesADeclaredSecret() {
    var provider = mock(SecretProvider.class);
    when(provider.getSecret(eq("DECLARED"), any())).thenReturn("resolved");
    var context =
        filteringContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.DECLARED")), provider);

    assertThat(replace(context, "token", "secrets.DECLARED")).isEqualTo("resolved");
  }

  @Test
  void secretFilterEnabled_resolvesADeclaredSecretInBraceSyntax() {
    var provider = mock(SecretProvider.class);
    when(provider.getSecret(eq("BRACED"), any())).thenReturn("resolved");
    var context =
        filteringContext(
            getInboundConnectorDefinition(Map.of("token", "{{ secrets.BRACED }}")), provider);

    assertThat(replace(context, "token", "{{ secrets.BRACED }}")).isEqualTo("resolved");
  }

  @Test
  void secretFilterEnabled_leavesAnUndeclaredSecret() {
    var provider = mock(SecretProvider.class);
    var context =
        filteringContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.DECLARED")), provider);

    assertThat(replace(context, "token", "secrets.INJECTED")).isEqualTo("secrets.INJECTED");
    verify(provider, never()).getSecret(eq("INJECTED"), any());
  }

  @Test
  void secretFilterEnabled_leavesASecretNamedOnlyByAnotherSecretsValue() {
    var provider = mock(SecretProvider.class);
    when(provider.getSecret(eq("CHAIN_ROOT"), any())).thenReturn("secrets.CHAINED");
    when(provider.getSecret(eq("CHAINED"), any())).thenReturn("leaked-value");
    var context =
        filteringContext(
            getInboundConnectorDefinition(Map.of("token", "{{secrets.CHAIN_ROOT}}")), provider);

    assertThat(replace(context, "token", "{{secrets.CHAIN_ROOT}}")).isEqualTo("secrets.CHAINED");
    verify(provider, never()).getSecret(eq("CHAINED"), any());
  }

  @Test
  void secretFilterEnabled_leavesASecretDeclaredOnlyOnASiblingField() {
    var provider = mock(SecretProvider.class);
    when(provider.getSecret(eq("SIBLING_ONLY"), any())).thenReturn("leaked-value");
    var context =
        filteringContext(
            getInboundConnectorDefinition(
                Map.of("tokenA", "plain", "tokenB", "secrets.SIBLING_ONLY")),
            provider);

    assertThat(replace(context, "tokenA", "secrets.SIBLING_ONLY"))
        .isEqualTo("secrets.SIBLING_ONLY");
    verify(provider, never()).getSecret(eq("SIBLING_ONLY"), any());
  }

  @Test
  void processElementSecretFilterScopesDottedPropertiesToTheirDeclaredPath() {
    var provider = mock(SecretProvider.class);
    when(provider.getSecret(eq("AUTH"), any())).thenReturn("resolved");
    var details = getInboundConnectorDefinition(Map.of("authentication.token", "secrets.AUTH"));
    var context =
        new DefaultProcessElementContext(
            details.connectorElements().getFirst(), (e) -> {}, provider, mapper, true);
    var matchingProbe =
        mapper
            .createObjectNode()
            .set("authentication", mapper.createObjectNode().put("token", "secrets.AUTH"));
    var siblingProbe =
        mapper
            .createObjectNode()
            .set("authentication", mapper.createObjectNode().put("type", "secrets.AUTH"));

    assertThat(
            context
                .getSecretHandler()
                .replaceSecrets(matchingProbe, new SecretContext("t"))
                .at("/authentication/token")
                .asText())
        .isEqualTo("resolved");
    assertThat(
            context
                .getSecretHandler()
                .replaceSecrets(siblingProbe, new SecretContext("t"))
                .at("/authentication/type")
                .asText())
        .isEqualTo("secrets.AUTH");
  }

  @Test
  void secretFilterDisabled_resolvesAnUndeclaredSecret() {
    var provider = mock(SecretProvider.class);
    when(provider.getSecret(eq("INJECTED"), any())).thenReturn("resolved");
    var context =
        new InboundConnectorContextImpl(
            provider,
            (e) -> {},
            getInboundConnectorDefinition(Map.of("token", "secrets.DECLARED")),
            null,
            (e) -> {},
            mapper,
            EvictingQueue.create(10));

    assertThat(replace(context, "token", "secrets.INJECTED")).isEqualTo("resolved");
  }

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

  // An activity log and a health error are both connector-authored text that routinely quotes an
  // API response, and both are read by anyone who can see the connector in Operate.
  //
  // Main's suite also covers health dedup (against the masked value, and the two-distinct-outage
  // case), which is not representable on this branch: reportHealth does not dedup here and writes
  // no activity log of its own.

  private InboundConnectorContextImpl contextOf(
      ValidInboundConnectorDetails details, SecretProvider provider) {
    return new InboundConnectorContextImpl(
        provider, (e) -> {}, details, null, (e) -> {}, mapper, EvictingQueue.create(10));
  }

  private InboundConnectorContextImpl cancellingContext(
      ValidInboundConnectorDetails details,
      SecretProvider provider,
      List<Throwable> cancellations) {
    return new InboundConnectorContextImpl(
        provider, (e) -> {}, details, null, cancellations::add, mapper, EvictingQueue.create(10));
  }

  private static Activity errorSaying(String message) {
    return Activity.level(Severity.ERROR).tag("test").message(message);
  }

  @Test
  void cancel_masksASecretInTheErrorTheRuntimePublishesAsHealth() {
    // the executable registry reports what a connector cancels with as
    // Health.down(exceptionThrown),
    // which publishes the throwable's toString() through the health endpoint
    var cancellations = new ArrayList<Throwable>();
    var context =
        cancellingContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.FOO")),
            secretProvider,
            cancellations);

    context.cancel(new IllegalStateException("api rejected bar"));

    assertThat(cancellations).singleElement();
    var published = Health.down(cancellations.getFirst());
    assertThat(published.getError().message()).doesNotContain("bar").contains("api rejected ***");
    // the type it replaced is still named, so the report stays diagnosable
    assertThat(published.getError().message()).contains("IllegalStateException");
  }

  @Test
  void cancel_keepsRetryMetadataWhileMaskingTheMessage() {
    // the registry restarts the executable from these, so redaction may not change them
    var cancellations = new ArrayList<Throwable>();
    var context =
        cancellingContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.FOO")),
            secretProvider,
            cancellations);

    context.cancel(
        ConnectorRetryException.builder()
            .errorCode("AUTH-401")
            .message("api rejected bar")
            .retries(4)
            .backoffDuration(Duration.ofSeconds(30))
            .build());

    assertThat(cancellations)
        .singleElement()
        .isInstanceOfSatisfying(
            ConnectorRetryException.class,
            retry -> {
              assertThat(retry.getMessage()).isEqualTo("api rejected ***");
              assertThat(retry.getRetries()).isEqualTo(4);
              assertThat(retry.getBackoffDuration()).isEqualTo(Duration.ofSeconds(30));
              assertThat(retry.getErrorCode()).isEqualTo("AUTH-401");
            });
  }

  @Test
  void cancel_withholdsTheMessageWhenSecretValuesCannotBeFetched() {
    var failingProvider = mock(SecretProvider.class);
    when(failingProvider.fetchAll(any(), any())).thenThrow(new RuntimeException("down"));
    var cancellations = new ArrayList<Throwable>();
    var context =
        cancellingContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.FOO")),
            failingProvider,
            cancellations);

    context.cancel(new IllegalStateException("api rejected bar"));

    assertThat(cancellations).singleElement();
    assertThat(cancellations.getFirst().getMessage()).doesNotContain("bar");
  }

  @Test
  void cancel_passesTheErrorThroughWhenThereIsNothingToRedact() {
    var cancellations = new ArrayList<Throwable>();
    var context =
        cancellingContext(
            getInboundConnectorDefinition(Map.of("token", "secrets.FOO")),
            secretProvider,
            cancellations);
    var original = new IllegalStateException("the broker closed the connection");

    context.cancel(original);

    assertThat(cancellations).singleElement().isSameAs(original);
  }

  @Test
  void log_masksASecretResolvedForThisConnector() {
    // given a connector declaring a secret this context can resolve
    var context =
        contextOf(getInboundConnectorDefinition(Map.of("token", "secrets.FOO")), secretProvider);

    // when an activity's message happens to carry the resolved value
    context.log(errorSaying("token was bar"));

    // then the logged message has the value masked
    assertThat(context.getLogs())
        .singleElement()
        .satisfies(log -> assertThat(log.message()).isEqualTo("token was ***"));
  }

  @Test
  void log_withholdsMessageWhenSecretValuesCannotBeFetched() {
    // given a provider that fails to resolve the declared secret
    var failingProvider = mock(SecretProvider.class);
    when(failingProvider.fetchAll(any(), any())).thenThrow(new RuntimeException("down"));
    var context =
        contextOf(getInboundConnectorDefinition(Map.of("token", "secrets.FOO")), failingProvider);

    // when
    context.log(errorSaying("token was bar"));

    // then the raw message is withheld rather than published unmasked
    assertThat(context.getLogs())
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
    var context =
        contextOf(getInboundConnectorDefinition(Map.of("token", "secrets.FOO")), secretProvider);

    // when the reported health carries the resolved value in its error
    context.reportHealth(Health.down(new RuntimeException("token was bar")));

    // then it is masked in the stored health, which is what the status endpoint serves
    assertThat(context.getHealth().getStatus()).isEqualTo(Health.Status.DOWN);
    assertThat(context.getHealth().getError().message()).doesNotContain("bar");
  }

  @Test
  void log_masksASecretThatRotatedAfterItWasBound() {
    // given a provider that resolves FOO to one value at bind time and a different one on re-read
    var rotatingProvider = mock(SecretProvider.class);
    when(rotatingProvider.getSecret(eq("FOO"), any())).thenReturn("old-value");
    when(rotatingProvider.fetchAll(any(), any())).thenReturn(List.of("new-value"));
    var context =
        contextOf(getInboundConnectorDefinition(Map.of("token", "secrets.FOO")), rotatingProvider);

    // when the bound value is used, binding first so it is actually substituted and captured
    context.getProperties();
    context.log(errorSaying("token was old-value"));

    // then the bound value is redacted even though a re-read would return a different one
    assertThat(context.getLogs())
        .last()
        .satisfies(log -> assertThat(log.message()).isEqualTo("token was ***"));
  }

  @Test
  void log_withholdsMessageWhenTheReReadComesBackShort() {
    // given a connector declaring two secrets of which only one resolves on re-read
    var partialProvider = mock(SecretProvider.class);
    when(partialProvider.fetchAll(any(), any())).thenReturn(List.of("only-one-value"));
    var context =
        contextOf(
            getInboundConnectorDefinition(Map.of("a", "secrets.FOO", "b", "secrets.BAR")),
            partialProvider);

    // when nothing was ever bound, so only the (incomplete) re-read is available
    context.log(errorSaying("token was leaked"));

    // then the message is withheld rather than published with only one of the two values masked
    assertThat(context.getLogs())
        .singleElement()
        .satisfies(log -> assertThat(log.message()).contains("withheld"));
  }

  @Test
  void log_masksASecretDeclaredOnlyByAGroupedSiblingElement() {
    // given two elements grouped under one deduplication ID: they agree on everything the grouping
    // compares, and differ only in resultExpression, which PROPERTIES_EXCLUDED_FROM_DEDUPLICATION
    // allows to differ -- so only the sibling declares SIBLING, and only the first element's
    // properties become the group's representative ones
    var provider =
        new SecretProvider() {
          @Override
          public String getSecret(String name, SecretContext context) {
            return switch (name) {
              case "FOO" -> "bar";
              case "SIBLING" -> "sibling-value";
              default -> null;
            };
          }
        };
    var context =
        contextOf(
            groupedInboundConnectorDefinition(
                Map.of("token", "secrets.FOO"),
                Map.of("resultExpression", "={token: secrets.SIBLING}")),
            provider);

    // when a message carries the value only the sibling element declares
    context.log(errorSaying("token was sibling-value"));

    // then it is masked too: the re-read covers every grouped element
    assertThat(context.getLogs())
        .singleElement()
        .satisfies(log -> assertThat(log.message()).isEqualTo("token was ***"));
  }

  @Test
  void canActivate_masksASecretTheActivatedElementResolvedBeforeItRotated() {
    // given a provider that answers one value now and a different one on the redaction re-read
    var rotatingProvider = mock(SecretProvider.class);
    when(rotatingProvider.getSecret(eq("FOO"), any())).thenReturn("old-value");
    when(rotatingProvider.fetchAll(any(), any())).thenReturn(List.of("new-value"));
    var details = getInboundConnectorDefinition(Map.of("token", "secrets.FOO"));
    var context = contextActivating(details, rotatingProvider);

    // when the connector reads the properties of the element it was handed, resolving FOO there
    // rather than on the connector-level context
    var activated = (ActivationCheckResult.Success.CanActivate) context.canActivate(Map.of());
    assertThat(activated.activatedElement().getElement())
        .isEqualTo(details.connectorElements().getFirst().element());
    assertThat(activated.activatedElement().getProperties()).containsEntry("token", "old-value");

    // then that value is redacted from what the connector reports afterwards, even though a re-read
    // now returns a different one
    context.log(errorSaying("token was old-value"));
    context.reportHealth(Health.down(new RuntimeException("token was old-value")));

    assertThat(context.getLogs())
        .last()
        .satisfies(log -> assertThat(log.message()).isEqualTo("token was ***"));
    assertThat(context.getHealth().getError().message()).doesNotContain("old-value");
  }

  @Test
  void correlate_masksASecretTheActivatedElementBoundBeforeItRotated() {
    // given the same rotation, reached through correlation rather than the activation check
    var rotatingProvider = mock(SecretProvider.class);
    when(rotatingProvider.getSecret(eq("FOO"), any())).thenReturn("old-value");
    when(rotatingProvider.fetchAll(any(), any())).thenReturn(List.of("new-value"));
    var details = getInboundConnectorDefinition(Map.of("token", "secrets.FOO"));
    var elementContext = elementContextOf(details, rotatingProvider);
    var correlationHandler = mock(InboundCorrelationHandler.class);
    when(correlationHandler.correlate(any(), any(CorrelationRequest.class)))
        .thenReturn(
            new CorrelationResult.Success.MessagePublished(elementContext, 1L, "<default>"));
    var context =
        new InboundConnectorContextImpl(
            rotatingProvider,
            (e) -> {},
            details,
            correlationHandler,
            (e) -> {},
            mapper,
            EvictingQueue.create(10));

    // when the connector binds the activated element's properties
    var result =
        (CorrelationResult.Success.MessagePublished)
            context.correlate(CorrelationRequest.builder().variables(Map.of()).build());
    assertThat(result.messageKey()).isEqualTo(1L);
    assertThat(result.activatedElement().bindProperties(Map.class))
        .containsEntry("token", "old-value");

    // then the bound value is redacted from the activity log
    context.log(errorSaying("token was old-value"));

    assertThat(context.getLogs())
        .last()
        .satisfies(log -> assertThat(log.message()).isEqualTo("token was ***"));
  }

  @Test
  void cancel_masksASecretTheActivatedElementResolvedBeforeItRotated() {
    // given the same rotation, reported through cancellation, which the runtime publishes as
    // Health.down(exceptionThrown)
    var rotatingProvider = mock(SecretProvider.class);
    when(rotatingProvider.getSecret(eq("FOO"), any())).thenReturn("old-value");
    when(rotatingProvider.fetchAll(any(), any())).thenReturn(List.of("new-value"));
    var details = getInboundConnectorDefinition(Map.of("token", "secrets.FOO"));
    List<Throwable> cancellations = new ArrayList<>();
    var context = contextActivating(details, rotatingProvider, cancellations);

    // when the connector resolves the secret through the element it was handed and then cancels
    // with an error quoting it
    var activated = (ActivationCheckResult.Success.CanActivate) context.canActivate(Map.of());
    activated.activatedElement().getProperties();
    context.cancel(new RuntimeException("token was old-value"));

    // then the value is redacted from what the runtime publishes
    assertThat(cancellations)
        .singleElement()
        .satisfies(error -> assertThat(error.getMessage()).doesNotContain("old-value"));
  }

  private InboundConnectorContextImpl contextActivating(
      ValidInboundConnectorDetails details, SecretProvider provider) {
    return contextActivating(details, provider, new ArrayList<>());
  }

  private InboundConnectorContextImpl contextActivating(
      ValidInboundConnectorDetails details,
      SecretProvider provider,
      List<Throwable> cancellations) {
    var correlationHandler = mock(InboundCorrelationHandler.class);
    when(correlationHandler.canActivate(any(), any()))
        .thenReturn(
            new ActivationCheckResult.Success.CanActivate(elementContextOf(details, provider)));
    return new InboundConnectorContextImpl(
        provider,
        (e) -> {},
        details,
        correlationHandler,
        cancellations::add,
        mapper,
        EvictingQueue.create(10));
  }

  private DefaultProcessElementContext elementContextOf(
      ValidInboundConnectorDetails details, SecretProvider provider) {
    return new DefaultProcessElementContext(
        details.connectorElements().getFirst(), (e) -> {}, provider, mapper);
  }

  private static ValidInboundConnectorDetails groupedInboundConnectorDefinition(
      Map<String, String> sharedProperties, Map<String, String> siblingOnlyProperties) {
    var shared = new HashMap<>(sharedProperties);
    shared.put("inbound.type", "io.camunda:connector:1");
    var siblingProperties = new HashMap<>(shared);
    siblingProperties.putAll(siblingOnlyProperties);
    var first =
        new InboundConnectorElement(
            shared,
            new StandaloneMessageCorrelationPoint("", "", null, null),
            new ProcessElement("bool", 0, 0, "first", "<default>"));
    var sibling =
        new InboundConnectorElement(
            siblingProperties,
            new StandaloneMessageCorrelationPoint("", "", null, null),
            new ProcessElement("bool", 0, 0, "sibling", "<default>"));
    var details = InboundConnectorDetails.of("grouped", List.of(first, sibling));
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
