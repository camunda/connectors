/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.sdk.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Factory that creates and tracks gRPC channels, providing cleanup functionality. */
public class ManagedChannelFactory implements AutoCloseable {
  private final boolean useTls;
  private final List<ManagedChannel> createdChannels;

  public ManagedChannelFactory(boolean useTls) {
    this.useTls = useTls;
    this.createdChannels = new ArrayList<>();
  }

  /**
   * Create a new channel and track it for cleanup.
   *
   * @param target the target address for the channel
   * @return the created channel
   */
  public ManagedChannel create(String target) {
    var builder = ManagedChannelBuilder.forTarget(target);
    if (useTls) {
      builder = builder.useTransportSecurity();
    } else {
      builder = builder.usePlaintext();
    }
    ManagedChannel channel = builder.build();
    createdChannels.add(channel);
    return channel;
  }

  @Override
  public void close() {
    for (ManagedChannel channel : createdChannels) {
      try {
        channel.shutdown();
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
          channel.shutdownNow();
          channel.awaitTermination(2, TimeUnit.SECONDS);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        channel.shutdownNow();
      } catch (Exception ignored) {
        // best-effort cleanup
      }
    }
    createdChannels.clear();
  }
}
