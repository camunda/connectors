/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers;

import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthentication;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpRequestConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "gcp", label = "GCP Provider")
public final class GcpProvider implements ProviderConfig, ExtractorConfig {

  @Valid @NotNull private GcpAuthentication authentication;

  private GcpRequestConfiguration configuration;

  public GcpRequestConfiguration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(GcpRequestConfiguration configuration) {
    this.configuration = configuration;
  }

  public GcpAuthentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(GcpAuthentication authentication) {
    this.authentication = authentication;
  }
}
