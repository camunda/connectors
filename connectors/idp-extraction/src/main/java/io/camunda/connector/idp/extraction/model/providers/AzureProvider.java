/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers;

import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.idp.extraction.model.providers.azure.AiFoundryConfig;
import io.camunda.connector.idp.extraction.model.providers.azure.DocumentIntelligenceConfiguration;

/**
 * @deprecated Legacy IDP extraction provider model, used only by {@link
 *     io.camunda.connector.idp.extraction.ExtractionConnectorFunction}. The structured /
 *     unstructured / classification connectors use the {@code request.common} provider model
 *     instead. Retained for backwards compatibility; no removal currently planned.
 */
@Deprecated(since = "8.9")
@TemplateSubType(id = "azure", label = "Azure Provider")
public final class AzureProvider implements ProviderConfig {

  private DocumentIntelligenceConfiguration documentIntelligenceConfiguration;
  private AiFoundryConfig aiFoundryConfig;

  public DocumentIntelligenceConfiguration getDocumentIntelligenceConfiguration() {
    return documentIntelligenceConfiguration;
  }

  public AiFoundryConfig getAiFoundryConfig() {
    return aiFoundryConfig;
  }

  public void setDocumentIntelligenceConfiguration(
      DocumentIntelligenceConfiguration documentIntelligenceConfiguration) {
    this.documentIntelligenceConfiguration = documentIntelligenceConfiguration;
  }

  public void setAiFoundryConfig(AiFoundryConfig aiFoundryConfig) {
    this.aiFoundryConfig = aiFoundryConfig;
  }
}
