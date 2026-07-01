/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.a2a.client.common.sdk.grpc.ManagedChannelFactory;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.MockedStatic;

class ManagedChannelFactoryTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldCreateMultipleChannels(boolean useTls) {
    var factory = new ManagedChannelFactory(useTls);

    ManagedChannel channel1 = factory.create("localhost:50051");
    ManagedChannel channel2 = factory.create("localhost:50052");
    ManagedChannel channel3 = factory.create("localhost:50053");

    assertThat(channel1).isNotNull();
    assertThat(channel2).isNotNull();
    assertThat(channel3).isNotNull();

    factory.close();
  }

  @SuppressWarnings({"rawtypes"})
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void usesPlaintextOrTransportSecurityBuilder(boolean useTls) {
    var factory = new ManagedChannelFactory(useTls);
    try (MockedStatic<ManagedChannelBuilder> staticMock = mockStatic(ManagedChannelBuilder.class)) {
      ManagedChannelBuilder builder = mock(ManagedChannelBuilder.class, Answers.RETURNS_SELF);
      ManagedChannel mockedChannel = mock(ManagedChannel.class);
      staticMock.when(() -> ManagedChannelBuilder.forTarget("target")).thenReturn(builder);
      when(builder.build()).thenReturn(mockedChannel);

      ManagedChannel result = factory.create("target");

      assertThat(result).isSameAs(mockedChannel);

      if (useTls) {
        verify(builder).useTransportSecurity();
        verify(builder, never()).usePlaintext();
      } else {
        verify(builder).usePlaintext();
        verify(builder, never()).useTransportSecurity();
      }
      verify(builder).build();
    }

    factory.close();
  }

  @SuppressWarnings({"rawtypes"})
  @Test
  void shouldShutdownAllChannelsOnClose() throws Exception {
    var factory = new ManagedChannelFactory(true);

    try (MockedStatic<ManagedChannelBuilder> staticMock = mockStatic(ManagedChannelBuilder.class)) {
      ManagedChannelBuilder builder = mock(ManagedChannelBuilder.class, Answers.RETURNS_SELF);
      ManagedChannel channel1 = mock(ManagedChannel.class);
      ManagedChannel channel2 = mock(ManagedChannel.class);

      staticMock.when(() -> ManagedChannelBuilder.forTarget("target1")).thenReturn(builder);
      when(builder.build()).thenReturn(channel1, channel2);
      when(channel1.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);
      when(channel1.isTerminated()).thenReturn(true);
      when(channel2.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);
      when(channel2.isTerminated()).thenReturn(true);

      factory.create("target1");
      staticMock.when(() -> ManagedChannelBuilder.forTarget("target2")).thenReturn(builder);
      factory.create("target2");

      factory.close();

      verify(channel1).shutdown();
      verify(channel1).awaitTermination(anyLong(), any(TimeUnit.class));
      verify(channel2).shutdown();
      verify(channel2).awaitTermination(anyLong(), any(TimeUnit.class));
    }
  }

  @SuppressWarnings({"rawtypes"})
  @Test
  void shouldForceShutdownIfAwaitTerminationTimesOut() throws Exception {
    var factory = new ManagedChannelFactory(true);

    try (MockedStatic<ManagedChannelBuilder> staticMock = mockStatic(ManagedChannelBuilder.class)) {
      ManagedChannelBuilder builder = mock(ManagedChannelBuilder.class, Answers.RETURNS_SELF);
      ManagedChannel channel = mock(ManagedChannel.class);

      staticMock.when(() -> ManagedChannelBuilder.forTarget("target")).thenReturn(builder);
      when(builder.build()).thenReturn(channel);
      when(channel.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(false);
      when(channel.isTerminated()).thenReturn(false);

      factory.create("target");
      factory.close();

      verify(channel).shutdown();
      verify(channel).shutdownNow();
      verify(channel).isTerminated();
    }
  }

  @SuppressWarnings({"rawtypes"})
  @Test
  void shouldHandleInterruptedExceptionDuringShutdown() throws Exception {
    var factory = new ManagedChannelFactory(false);

    try (MockedStatic<ManagedChannelBuilder> staticMock = mockStatic(ManagedChannelBuilder.class)) {
      ManagedChannelBuilder builder = mock(ManagedChannelBuilder.class, Answers.RETURNS_SELF);
      ManagedChannel channel = mock(ManagedChannel.class);

      staticMock.when(() -> ManagedChannelBuilder.forTarget("target")).thenReturn(builder);
      when(builder.build()).thenReturn(channel);
      when(channel.awaitTermination(anyLong(), any(TimeUnit.class)))
          .thenThrow(new InterruptedException("Test interruption"));

      factory.create("target");

      // Should not throw and should call shutdownNow
      factory.close();

      verify(channel).shutdown();
      verify(channel).shutdownNow();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      // Clear the interrupt flag
      Thread.interrupted();
    }
  }

  @SuppressWarnings({"rawtypes"})
  @Test
  void shouldContinueShutdownEvenIfOneChannelThrows() throws Exception {
    var factory = new ManagedChannelFactory(true);

    try (MockedStatic<ManagedChannelBuilder> staticMock = mockStatic(ManagedChannelBuilder.class)) {
      ManagedChannelBuilder builder = mock(ManagedChannelBuilder.class, Answers.RETURNS_SELF);
      ManagedChannel channel1 = mock(ManagedChannel.class);
      ManagedChannel channel2 = mock(ManagedChannel.class);

      staticMock.when(() -> ManagedChannelBuilder.forTarget("target1")).thenReturn(builder);
      staticMock.when(() -> ManagedChannelBuilder.forTarget("target2")).thenReturn(builder);

      when(builder.build()).thenReturn(channel1, channel2);
      when(channel1.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);
      when(channel1.isTerminated()).thenReturn(true);
      when(channel2.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);
      when(channel2.isTerminated()).thenReturn(true);

      // First channel throws on shutdown
      doThrow(new RuntimeException("Shutdown failed")).when(channel1).shutdown();

      factory.create("target1");
      factory.create("target2");

      // Should not throw and should still shut down second channel
      factory.close();

      verify(channel1).shutdown();
      verify(channel2).shutdown();
      verify(channel2).awaitTermination(anyLong(), any(TimeUnit.class));
    }
  }

  @Test
  void shouldClearChannelsAfterClose() {
    var factory = new ManagedChannelFactory(true);

    factory.create("localhost:50051");
    factory.create("localhost:50052");

    factory.close();

    // Calling close again should be safe (no channels to close)
    factory.close();
  }

  @Test
  void shouldHandleCloseWithNoChannels() {
    var factory = new ManagedChannelFactory(true);

    // Should not throw
    factory.close();
  }
}
