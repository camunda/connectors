/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import io.a2a.client.ClientEvent;
import io.a2a.spec.AgentCard;
import java.util.function.BiConsumer;

public interface A2aSdkClientFactory {
  A2aSdkClient buildClient(
      AgentCard agentCard, BiConsumer<ClientEvent, AgentCard> consumer, A2aSdkClientConfig config);
}
