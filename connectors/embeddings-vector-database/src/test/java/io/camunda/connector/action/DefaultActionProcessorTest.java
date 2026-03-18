/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.action;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.action.embed.DefaultEmbeddingActionProcessor;
import io.camunda.connector.action.embed.EmbeddingActionProcessor;
import io.camunda.connector.action.retrieve.DefaultRetrievingActionProcessor;
import io.camunda.connector.action.retrieve.RetrievingActionProcessor;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.fixture.EmbeddingsVectorDBRequestFixture;
import io.camunda.connector.http.client.proxy.EnvironmentProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class DefaultActionProcessorTest {

  @SystemStub private EnvironmentVariables environment;

  private final EmbeddingActionProcessor embeddingProcessor =
      Mockito.mock(EmbeddingActionProcessor.class);
  private final RetrievingActionProcessor retrievingProcessor =
      Mockito.mock(RetrievingActionProcessor.class);
  private final DocumentFactory documentFactory = Mockito.mock(DocumentFactory.class);

  @Test
  void handleEmbedRequest() {
    final var actionProcessor = new DefaultActionProcessor(embeddingProcessor, retrievingProcessor);
    final var embedRequest = EmbeddingsVectorDBRequestFixture.createDefaultEmbedOperation();

    actionProcessor.handleFlow(embedRequest, documentFactory);

    Mockito.verify(embeddingProcessor).embed(embedRequest);
  }

  @Test
  void handleRetrieveRequest() {
    final var actionProcessor = new DefaultActionProcessor(embeddingProcessor, retrievingProcessor);
    final var retrieveRequest = EmbeddingsVectorDBRequestFixture.createDefaultRetrieve();

    actionProcessor.handleFlow(retrieveRequest, documentFactory);

    Mockito.verify(retrievingProcessor).retrieve(retrieveRequest, documentFactory);
  }

  @Test
  void proxySupportEnabled_byDefault() {
    assertProxyConfigurationUsed(EnvironmentProxyConfiguration.class);
  }

  @Test
  void proxySupportDisabled_whenEnvVarIsFalse() {
    environment.set("CAMUNDA_CONNECTOR_VECTORDB_HTTP_PROXYSUPPORT_ENABLED", "false");
    assertProxyConfigurationIsNone();
  }

  @Test
  void proxySupportDisabled_caseInsensitive() {
    environment.set("CAMUNDA_CONNECTOR_VECTORDB_HTTP_PROXYSUPPORT_ENABLED", "FALSE");
    assertProxyConfigurationIsNone();
  }

  private void assertProxyConfigurationIsNone() {
    ProxyConfiguration captured = captureProxyConfiguration();
    assertThat(captured).isSameAs(ProxyConfiguration.NONE);
  }

  private void assertProxyConfigurationUsed(Class<? extends ProxyConfiguration> expectedType) {
    ProxyConfiguration captured = captureProxyConfiguration();
    assertThat(captured).isInstanceOf(expectedType);
  }

  private ProxyConfiguration captureProxyConfiguration() {
    List<ProxyConfiguration> capturedConfigs = new ArrayList<>();

    try (MockedConstruction<DefaultEmbeddingActionProcessor> embeddingCtor =
            Mockito.mockConstruction(
                DefaultEmbeddingActionProcessor.class,
                (mock, context) ->
                    capturedConfigs.add((ProxyConfiguration) context.arguments().get(0)));
        MockedConstruction<DefaultRetrievingActionProcessor> retrievingCtor =
            Mockito.mockConstruction(DefaultRetrievingActionProcessor.class)) {

      new DefaultActionProcessor();

      assertThat(capturedConfigs).hasSize(1);
      return capturedConfigs.get(0);
    }
  }
}
