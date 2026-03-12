/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.action;

import io.camunda.connector.action.embed.DefaultEmbeddingActionProcessor;
import io.camunda.connector.action.embed.EmbeddingActionProcessor;
import io.camunda.connector.action.retrieve.DefaultRetrievingActionProcessor;
import io.camunda.connector.action.retrieve.RetrievingActionProcessor;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.http.client.proxy.EnvironmentProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.model.EmbeddingsVectorDBRequest;
import io.camunda.connector.model.operation.EmbedDocumentOperation;
import io.camunda.connector.model.operation.RetrieveDocumentOperation;

public class DefaultActionProcessor {

  private final EmbeddingActionProcessor embeddingActionProcessor;
  private final RetrievingActionProcessor retrievingActionProcessor;

  private static final String PROXY_SUPPORT_ENABLED_ENV_VAR =
      "CAMUNDA_CONNECTOR_VECTORDB_HTTP_PROXYSUPPORT_ENABLED";

  public DefaultActionProcessor() {
    this(resolveProxyConfiguration());
  }

  private DefaultActionProcessor(ProxyConfiguration proxyConfiguration) {
    this(
        new DefaultEmbeddingActionProcessor(proxyConfiguration),
        new DefaultRetrievingActionProcessor(proxyConfiguration));
  }

  public DefaultActionProcessor(
      final EmbeddingActionProcessor embeddingActionProcessor,
      final RetrievingActionProcessor retrievingActionProcessor) {
    this.embeddingActionProcessor = embeddingActionProcessor;
    this.retrievingActionProcessor = retrievingActionProcessor;
  }

  public Object handleFlow(EmbeddingsVectorDBRequest request, DocumentFactory documentFactory) {
    return switch (request.vectorDatabaseConnectorOperation()) {
      case EmbedDocumentOperation ignored -> embeddingActionProcessor.embed(request);
      case RetrieveDocumentOperation ignored ->
          retrievingActionProcessor.retrieve(request, documentFactory);
    };
  }

  private static ProxyConfiguration resolveProxyConfiguration() {
    // defaults to true
    String envValue = System.getenv(PROXY_SUPPORT_ENABLED_ENV_VAR);
    if ("false".equalsIgnoreCase(envValue)) {
      return ProxyConfiguration.NONE;
    }

    return new EnvironmentProxyConfiguration(true);
  }
}
