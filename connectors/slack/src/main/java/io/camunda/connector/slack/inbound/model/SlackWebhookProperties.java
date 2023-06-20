/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.inbound.model;

import com.slack.api.app_backend.SlackSignature;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;

public class SlackWebhookProperties {

  private final InboundConnectorProperties genericProperties;
  @Secret private String context;
  @Secret private String signingSecret;

  public SlackWebhookProperties(InboundConnectorProperties properties) {
    this.genericProperties = properties;
    this.context = genericProperties.getProperties().get("inbound.context");
    this.signingSecret = genericProperties.getProperties().get("inbound.slackSigningSecret");
  }

  public SlackSignature.Verifier signatureVerifier() {
    return new SlackSignature.Verifier(new SlackSignature.Generator(signingSecret));
  }

  public InboundConnectorProperties getGenericProperties() {
    return genericProperties;
  }

  public String getContext() {
    return context;
  }

  public void setContext(String context) {
    this.context = context;
  }

  public String getSigningSecret() {
    return signingSecret;
  }

  public void setSigningSecret(String signingSecret) {
    this.signingSecret = signingSecret;
  }
}
