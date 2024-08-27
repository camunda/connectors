package io.camunda.connector.email.core.jakarta;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.inbound.model.EmailProperties;
import jakarta.mail.*;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.event.MessageCountListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.angus.mail.imap.IMAPFolder;

public class JakartaEmailListener {

  private final InboundConnectorContext connectorContext;
  private final EmailProperties emailProperties;
  private final JakartaSessionFactory sessionFactory;
  private ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

  public JakartaEmailListener(
      InboundConnectorContext context,
      EmailProperties emailProperties,
      JakartaSessionFactory sessionFactory) {
    this.connectorContext = context;
    this.emailProperties = emailProperties;
    this.sessionFactory = sessionFactory;
  }

  public static JakartaEmailListener create(
      InboundConnectorContext context, JakartaSessionFactory sessionFactory) {
    return new JakartaEmailListener(
        context, context.bindProperties(EmailProperties.class), sessionFactory);
  }

  public void start() {
    executorService.execute(this::consume);
  }

  private void consume() {
    Session session =
        this.sessionFactory.createSession(
            this.emailProperties.getData().getImapConfig(),
            this.emailProperties.getAuthentication());
    try {
      Store store = session.getStore();
      IMAPFolder inbox =
          (IMAPFolder) store.getFolder(this.emailProperties.getData().getFolderToListen());

      inbox.addMessageCountListener(
          new MessageCountListener() {
            @Override
            public void messagesAdded(MessageCountEvent e) {}

            @Override
            public void messagesRemoved(MessageCountEvent e) {}
          });
    } catch (MessagingException e) {
      this.connectorContext.reportHealth(Health.down(e));
    }
  }
}
