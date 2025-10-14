/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.impl;

import io.camunda.connector.agenticai.a2a.client.api.A2aChannelProvider;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class A2aChannelProviderImpl implements A2aChannelProvider {

  private final boolean useTls;

  public A2aChannelProviderImpl(boolean useTls) {
    this.useTls = useTls;
  }

  @Override
  public ManagedChannel create(String target) {
    var builder = ManagedChannelBuilder.forTarget(target);
    if (useTls) {
      builder = builder.useTransportSecurity();
    } else {
      builder = builder.usePlaintext();
    }
    return builder.build();
  }
}
