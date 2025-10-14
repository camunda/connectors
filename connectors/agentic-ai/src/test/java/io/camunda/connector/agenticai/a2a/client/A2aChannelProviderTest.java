/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.a2a.client.impl.A2aChannelProviderImpl;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.MockedStatic;

class A2aChannelProviderTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void createsAChannel(boolean useTls) throws InterruptedException {
    var provider = new A2aChannelProviderImpl(useTls);

    ManagedChannel channel = provider.create("localhost:12345");

    assertThat(channel).as("ManagedChannel should be created with plaintext config").isNotNull();
    channel.shutdownNow();
    channel.awaitTermination(1, TimeUnit.SECONDS);
  }

  @SuppressWarnings({"rawtypes"})
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void usesPlaintextOrTransportSecurityBuilder(boolean useTls) {
    var provider = new A2aChannelProviderImpl(useTls);
    try (MockedStatic<ManagedChannelBuilder> staticMock = mockStatic(ManagedChannelBuilder.class)) {
      ManagedChannelBuilder builder = mock(ManagedChannelBuilder.class, Answers.RETURNS_SELF);
      ManagedChannel mockedChannel = mock(ManagedChannel.class);

      staticMock.when(() -> ManagedChannelBuilder.forTarget("target")).thenReturn(builder);
      when(builder.build()).thenReturn(mockedChannel);

      ManagedChannel result = provider.create("target");

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
  }
}
