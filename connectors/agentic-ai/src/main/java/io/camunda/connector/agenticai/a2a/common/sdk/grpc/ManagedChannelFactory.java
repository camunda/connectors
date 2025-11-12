/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.common.sdk.grpc;

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
    // Initiate shutdown on all channels first (non-blocking)
    for (ManagedChannel channel : createdChannels) {
      try {
        channel.shutdown();
      } catch (Exception ignored) {
        // best-effort cleanup
      }
    }

    // Give all channels a total of 5 seconds to terminate gracefully
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    for (ManagedChannel channel : createdChannels) {
      try {
        long remaining = deadline - System.nanoTime();
        if (remaining > 0 && !channel.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
          channel.shutdownNow();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        channel.shutdownNow();
      } catch (Exception ignored) {
        // best-effort cleanup
      }
    }

    // Give channels that needed force shutdown another 2 seconds total
    deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    for (ManagedChannel channel : createdChannels) {
      try {
        if (!channel.isTerminated()) {
          long remaining = deadline - System.nanoTime();
          if (remaining > 0) {
            channel.awaitTermination(remaining, TimeUnit.NANOSECONDS);
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // Continue with remaining channels despite interruption
      } catch (Exception ignored) {
        // best-effort cleanup
      }
    }

    createdChannels.clear();
  }
}
