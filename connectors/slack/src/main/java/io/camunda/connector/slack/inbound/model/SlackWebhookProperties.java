/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.inbound.model;

import com.slack.api.app_backend.SlackSignature;
import java.util.Map;

public class SlackWebhookProperties {

  private final Map<String, String> genericProperties;
  private String context;
  private String signingSecret;

  public SlackWebhookProperties(Map<String, Object> properties) {
    this.genericProperties = (Map<String, String>) properties.get("inbound");
    this.context = genericProperties.get("context");
    this.signingSecret = genericProperties.get("slackSigningSecret");
  }

  public SlackSignature.Verifier signatureVerifier() {
    return new SlackSignature.Verifier(new SlackSignature.Generator(signingSecret));
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
