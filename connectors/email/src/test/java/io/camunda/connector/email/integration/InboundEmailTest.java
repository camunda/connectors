/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.client.jakarta.inbound.JakartaEmailListener;
import io.camunda.connector.email.config.CryptographicProtocol;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.email.inbound.model.EmailInboundConnectorProperties;
import io.camunda.connector.email.inbound.model.EmailListenerConfig;
import io.camunda.connector.email.inbound.model.HandlingStrategy;
import jakarta.mail.Flags;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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

  @AfterEach
  public void afterEach() {
    this.jakartaEmailListener.stopListener();
  }

  @BeforeEach
  public void beforeEach() {
    super.reset();
  }

  @ParameterizedTest
  @MethodSource("createEmailInboundConnectorProperties")
  public void shouldReceiveEmail(EmailInboundConnectorProperties emailInboundConnectorProperties)
      throws MessagingException {
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

    doNothing().when(inboundConnectorContext).log(any());
    when(inboundConnectorContext.bindProperties(EmailInboundConnectorProperties.class))
        .thenReturn(emailInboundConnectorProperties1);
    when(inboundConnectorContext.correlate(any()))
        .thenReturn(new CorrelationResult.Success.ProcessInstanceCreated(null, null, null));
    when(inboundConnectorContext.canActivate(any()))
        .thenReturn(new ActivationCheckResult.Success.CanActivate(null));

    jakartaEmailListener.startListener(inboundConnectorContext);

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

  private void assertFlagOnLastEmail(HandlingStrategy handlingStrategy) throws MessagingException {
    Assertions.assertTrue(
        Arrays.stream(super.getLastReceivedEmails())
            .findFirst()
            .get()
            .getFlags()
            .contains(
                switch (handlingStrategy) {
                  case READ -> Flags.Flag.SEEN;
                  case DELETE, MOVE -> Flags.Flag.DELETED;
                }));
  }
}
