package io.camunda.connector.email.client.jakarta;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.client.jakarta.inbound.PollingManager;
import io.camunda.connector.email.inbound.model.EmailListenerConfig;
import io.camunda.connector.email.inbound.model.HandlingStrategy;
import io.camunda.connector.email.inbound.model.UnseenPollingConfig;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class PollingManagerTest {

  @Test
  void poll() throws MessagingException {
    InboundConnectorContext connectorContext = mock(InboundConnectorContext.class);
    Folder folder = mock(Folder.class);
    Store store = mock(Store.class);
    EmailListenerConfig emailListenerConfig = mock(EmailListenerConfig.class);
    UnseenPollingConfig unseenPollingConfig = mock(UnseenPollingConfig.class);
    TestImapMessage message =
        TestImapMessage.builder()
            .setTo(List.of("recipient@example.com"))
            .setMessageId("messageId")
            .setFrom("sender")
            .setSubject("subject")
            .setBody("body")
            .createTestMessage();

    PollingManager pollingManager = PollingManager.create(connectorContext, folder, store);

    when(emailListenerConfig.pollingConfig()).thenReturn(unseenPollingConfig);
    when(folder.getMessages()).thenReturn(new Message[] {message});
    when(folder.search(any(), any())).thenReturn(new Message[] {message});
    when(unseenPollingConfig.handlingStrategy()).thenReturn(HandlingStrategy.READ);
    pollingManager.poll(emailListenerConfig);

    verify(connectorContext, times(1)).correlateWithResult(argThat(Objects::nonNull));
  }
}
