/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import org.a2aproject.sdk.spec.AgentCard;

/**
 * Opaque handle to an Agent Card of the A2A SDK. It lets code outside of the SDK adapter package
 * pass an Agent Card from the fetcher to the client factory without depending on the SDK types.
 */
public record A2aSdkAgentCard(AgentCard sdkAgentCard) {

  public String name() {
    return sdkAgentCard.name();
  }
}
