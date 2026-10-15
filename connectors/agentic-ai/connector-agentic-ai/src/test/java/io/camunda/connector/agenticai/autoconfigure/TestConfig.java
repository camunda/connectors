/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.a2a.client.configuration.A2aClientAutoConfiguration;
import io.camunda.connector.agenticai.a2a.integration.configuration.A2aClientAgenticToolConfiguration;
import io.camunda.connector.agenticai.adhoctoolsschema.configuration.AdHocToolsSchemaConnectorAutoConfiguration;
import io.camunda.connector.agenticai.common.configuration.AgenticAiCommonAutoConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientAutoConfiguration;
import io.camunda.connector.agenticai.mcp.integration.configuration.McpDiscoveryConfiguration;
import io.camunda.connector.agenticai.tools.schema.configuration.AgenticAiToolsSchemaAutoConfiguration;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.context.annotation.Bean;

class TestConfig {

  /**
   * All auto-configurations of the agentic AI connectors, as registered via {@code
   * AutoConfiguration.imports}.
   */
  static final AutoConfigurations ALL_AUTO_CONFIGURATIONS =
      AutoConfigurations.of(
          AgenticAiCommonAutoConfiguration.class,
          AgenticAiToolsSchemaAutoConfiguration.class,
          AdHocToolsSchemaConnectorAutoConfiguration.class,
          A2aClientAutoConfiguration.class,
          A2aClientAgenticToolConfiguration.class,
          McpClientAutoConfiguration.class,
          McpDiscoveryConfiguration.class,
          AgenticAiConnectorsAutoConfiguration.class);

  @Bean
  @ConnectorsObjectMapper
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  public OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver() {
    return mock(OAuthClientCredentialsTokenResolver.class);
  }

  @Bean
  public CamundaClient camundaClient() {
    return mock(CamundaClient.class);
  }

  /**
   * Registered here rather than relying on the runtime auto-configuration, which this slice does
   * not load.
   */
  @Bean
  public PhysicalTenantClientSelector physicalTenantClientSelector(
      ObjectProvider<CamundaClient> camundaClientProvider) {
    return new PhysicalTenantClientSelector(camundaClientProvider);
  }

  @Bean
  public DocumentFactory documentFactory() {
    return mock(DocumentFactory.class);
  }

  @Bean
  public CamundaDocumentStore documentStore() {
    return mock(CamundaDocumentStore.class);
  }
}
