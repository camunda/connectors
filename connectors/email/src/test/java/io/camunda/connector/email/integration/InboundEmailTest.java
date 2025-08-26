/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.integration;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.client.jakarta.inbound.JakartaEmailListener;
import io.camunda.connector.email.config.CryptographicProtocol;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.email.inbound.model.*;
import jakarta.mail.Flags;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class InboundEmailTest extends BaseEmailTest {

  JakartaEmailListener jakartaEmailListener = new JakartaEmailListener();

  private static List<EmailInboundConnectorProperties> createEmailInboundConnectorProperties() {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    try {
      return objectMapper.readValue(
          Files.readString(
              Path.of("src/test/resources/integration/inbound-connector-happy-path.json")),
          new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static @NotNull Collection<Flags.Flag> getFlag(HandlingStrategy handlingStrategy) {
    return switch (handlingStrategy) {
      case READ -> List.of(Flags.Flag.SEEN);
      case DELETE -> List.of(Flags.Flag.DELETED);
      case MOVE -> List.of(Flags.Flag.DELETED, Flags.Flag.SEEN);
    };
  }

  @BeforeEach
  public void beforeEach() {
    super.reset();
  }

  @AfterEach
  public void afterEach() {
    this.jakartaEmailListener.stopListener();
  }

  @ParameterizedTest
  @MethodSource("createEmailInboundConnectorProperties")
  public void shouldReceiveEmail(EmailInboundConnectorProperties emailInboundConnectorProperties) {
    InboundConnectorContext inboundConnectorContext = mock(InboundConnectorContext.class);

    ImapConfig pollingConfig =
        new ImapConfig(
            "localhost", Integer.valueOf(super.getUnsecureImapPort()), CryptographicProtocol.NONE);

    EmailInboundConnectorProperties emailInboundConnectorProperties1 =
        new EmailInboundConnectorProperties(
            emailInboundConnectorProperties.authentication(),
            new EmailListenerConfig(
                pollingConfig,
                emailInboundConnectorProperties.data().folderToListen(),
                emailInboundConnectorProperties.data().pollingWaitTime(),
                emailInboundConnectorProperties.data().pollingConfig()));

    doNothing().when(inboundConnectorContext).log(any(Consumer.class));
    when(inboundConnectorContext.bindProperties(EmailInboundConnectorProperties.class))
        .thenReturn(emailInboundConnectorProperties1);
    when(inboundConnectorContext.correlate(any()))
        .thenReturn(new CorrelationResult.Success.ProcessInstanceCreated(null, null, null));
    when(inboundConnectorContext.canActivate(any()))
        .thenReturn(new ActivationCheckResult.Success.CanActivate(null));
    when(inboundConnectorContext.bindProperties(EmailInboundConnectorProperties.class))
        .thenReturn(emailInboundConnectorProperties1);
    when(inboundConnectorContext.correlate(any()))
        .thenReturn(new CorrelationResult.Success.ProcessInstanceCreated(null, null, null));
    when(inboundConnectorContext.canActivate(any()))
        .thenReturn(new ActivationCheckResult.Success.CanActivate(null));

    this.jakartaEmailListener.startListener(inboundConnectorContext);

    super.sendEmail("camunda@test.com", "Subject", "Content");

    verify(inboundConnectorContext, timeout(3000).times(1)).canActivate(any());
    verify(inboundConnectorContext, timeout(3000).times(1)).correlate(any());
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertFlagOnLastEmail(
                    emailInboundConnectorProperties.data().pollingConfig().handlingStrategy()));
  }

  @Test
  public void ForwardErrorToUpstreamAfterCorrelationEmailNotConsumed() {
    InboundConnectorContext inboundConnectorContext = mock(InboundConnectorContext.class);

    EmailInboundConnectorProperties emailInboundConnectorProperties =
        new EmailInboundConnectorProperties(
            new SimpleAuthentication("test@camunda.com", "password"),
            new EmailListenerConfig(
                new ImapConfig(
                    "localhost",
                    Integer.valueOf(super.getUnsecureImapPort()),
                    CryptographicProtocol.NONE),
                "",
                Duration.of(2, ChronoUnit.SECONDS),
                new PollUnseen(HandlingStrategy.READ, "")));

    doNothing().when(inboundConnectorContext).log(any(Consumer.class));
    when(inboundConnectorContext.bindProperties(EmailInboundConnectorProperties.class))
        .thenReturn(emailInboundConnectorProperties);
    when(inboundConnectorContext.correlate(any()))
        .thenReturn(new CorrelationResult.Failure.ActivationConditionNotMet(false));
    when(inboundConnectorContext.canActivate(any()))
        .thenReturn(new ActivationCheckResult.Success.CanActivate(null));

    this.jakartaEmailListener.startListener(inboundConnectorContext);
    super.sendEmail("camunda@test.com", "Subject", "Content");

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                Assertions.assertTrue(
                    Arrays.stream(
                            Arrays.stream(super.getLastReceivedEmails())
                                .findFirst()
                                .get()
                                .getFlags()
                                .getSystemFlags())
                        .toList()
                        .isEmpty()));
  }

  @Test
  public void ForwardErrorToUpstreamAfterCorrelationEmailConsumed() {
    InboundConnectorContext inboundConnectorContext = mock(InboundConnectorContext.class);

    EmailInboundConnectorProperties emailInboundConnectorProperties =
        new EmailInboundConnectorProperties(
            new SimpleAuthentication("test@camunda.com", "password"),
            new EmailListenerConfig(
                new ImapConfig(
                    "localhost",
                    Integer.valueOf(super.getUnsecureImapPort()),
                    CryptographicProtocol.NONE),
                "",
                Duration.of(2, ChronoUnit.SECONDS),
                new PollUnseen(HandlingStrategy.READ, "")));

    doNothing().when(inboundConnectorContext).log(any(Consumer.class));
    when(inboundConnectorContext.bindProperties(EmailInboundConnectorProperties.class))
        .thenReturn(emailInboundConnectorProperties);
    when(inboundConnectorContext.correlate(any()))
        .thenReturn(new CorrelationResult.Failure.ActivationConditionNotMet(true));
    when(inboundConnectorContext.canActivate(any()))
        .thenReturn(new ActivationCheckResult.Success.CanActivate(null));

    this.jakartaEmailListener.startListener(inboundConnectorContext);
    super.sendEmail("camunda@test.com", "Subject", "Content");

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                Assertions.assertTrue(
                    Arrays.stream(
                            Arrays.stream(super.getLastReceivedEmails())
                                .findFirst()
                                .get()
                                .getFlags()
                                .getSystemFlags())
                        .toList()
                        .contains(Flags.Flag.SEEN)));
  }

  @Test
  public void IgnoreStrategyAfterCorrelationEmailConsumed() {
    InboundConnectorContext inboundConnectorContext = mock(InboundConnectorContext.class);

    EmailInboundConnectorProperties emailInboundConnectorProperties =
        new EmailInboundConnectorProperties(
            new SimpleAuthentication("test@camunda.com", "password"),
            new EmailListenerConfig(
                new ImapConfig(
                    "localhost",
                    Integer.valueOf(super.getUnsecureImapPort()),
                    CryptographicProtocol.NONE),
                "",
                Duration.of(2, ChronoUnit.SECONDS),
                new PollUnseen(HandlingStrategy.READ, "")));

    doNothing().when(inboundConnectorContext).log(any(Consumer.class));
    when(inboundConnectorContext.bindProperties(EmailInboundConnectorProperties.class))
        .thenReturn(emailInboundConnectorProperties);
    when(inboundConnectorContext.correlate(any()))
        .thenReturn(new CorrelationResult.Failure.ZeebeClientStatus("ZeebeIssue", "Error"));
    when(inboundConnectorContext.canActivate(any()))
        .thenReturn(new ActivationCheckResult.Success.CanActivate(null));

    this.jakartaEmailListener.startListener(inboundConnectorContext);
    super.sendEmail("camunda@test.com", "Subject", "Content");

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                Assertions.assertTrue(
                    Arrays.stream(
                            Arrays.stream(super.getLastReceivedEmails())
                                .findFirst()
                                .get()
                                .getFlags()
                                .getSystemFlags())
                        .toList()
                        .isEmpty()));
  }

  private void assertFlagOnLastEmail(HandlingStrategy handlingStrategy) throws MessagingException {
    Assertions.assertTrue(
        Arrays.stream(
                Arrays.stream(super.getLastReceivedEmails())
                    .findFirst()
                    .get()
                    .getFlags()
                    .getSystemFlags())
            .toList()
            .containsAll(getFlag(handlingStrategy)));
  }
}
