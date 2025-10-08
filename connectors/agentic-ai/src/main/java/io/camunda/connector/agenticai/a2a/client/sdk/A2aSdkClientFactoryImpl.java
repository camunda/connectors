/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.sdk;

import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.a2a.spec.A2AClientException;
import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.api.A2aSdkClientFactory;
import java.util.function.BiConsumer;

public class A2aSdkClientFactoryImpl implements A2aSdkClientFactory {

  @Override
  public Client buildClient(AgentCard agentCard, BiConsumer<ClientEvent, AgentCard> consumer) {
    try {
      return Client.builder(agentCard)
          .clientConfig(new ClientConfig.Builder().setStreaming(false).setPolling(false).build())
          .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
          .addConsumer(consumer)
          .build();
    } catch (A2AClientException e) {
      throw new RuntimeException(e);
    }
  }
}
