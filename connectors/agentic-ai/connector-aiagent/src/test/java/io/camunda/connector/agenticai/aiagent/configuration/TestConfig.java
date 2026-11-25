/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.configuration;

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.configuration.AgenticAiLangChain4JAutoConfiguration;
import io.camunda.connector.agenticai.common.configuration.AgenticAiCommonAutoConfiguration;
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

  /** The auto-configurations the AI Agent connector builds on, plus its own. */
  static final AutoConfigurations ALL_AUTO_CONFIGURATIONS =
      AutoConfigurations.of(
          AgenticAiCommonAutoConfiguration.class,
          AgenticAiToolsSchemaAutoConfiguration.class,
          AgenticAiConnectorsAutoConfiguration.class,
          AgenticAiLangChain4JAutoConfiguration.class);

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
