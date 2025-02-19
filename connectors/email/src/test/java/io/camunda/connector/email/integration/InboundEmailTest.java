package io.camunda.connector.email.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.client.jakarta.inbound.JakartaEmailListener;
import io.camunda.connector.email.config.CryptographicProtocol;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.email.inbound.model.EmailInboundConnectorProperties;
import io.camunda.connector.email.inbound.model.EmailListenerConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import io.camunda.connector.email.inbound.model.HandlingStrategy;
import jakarta.mail.Flags;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.verification.VerificationMode;

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

  @BeforeEach
  public void beforeEach() {
    super.reset();
  }

  @ParameterizedTest
  @MethodSource("createEmailInboundConnectorProperties")
  public void shouldReceiveEmail(
      EmailInboundConnectorProperties emailInboundConnectorProperties) throws MessagingException {
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

    when(inboundConnectorContext.bindProperties(EmailInboundConnectorProperties.class))
        .thenReturn(emailInboundConnectorProperties1);

    jakartaEmailListener.startListener(inboundConnectorContext);

    super.sendEmail("camunda@test.com", "Subject", "Content");

    verify(inboundConnectorContext, timeout(3000).times(1)).canActivate(any());
    verify(inboundConnectorContext, timeout(3000).times(1)).correlateWithResult(any());

    assertFlagOnLastEmail(
        emailInboundConnectorProperties.data().pollingConfig().handlingStrategy());
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
