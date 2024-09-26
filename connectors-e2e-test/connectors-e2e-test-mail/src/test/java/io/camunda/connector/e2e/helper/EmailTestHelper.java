package io.camunda.connector.e2e.helper;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class EmailTestHelper {
  public static Message getLastEmailFromInbucket(String host, String port, String receiver) {
    Properties properties = new Properties();
    properties.put("mail.store.protocol", "pop3");
    properties.put("mail.pop3.host", host);
    properties.put("mail.pop3.port", port);
    properties.put("mail.pop3.auth", true);
    Session session = Session.getDefaultInstance(properties);

    try {
      Store store = session.getStore("pop3");
      store.connect(receiver, "password");
      Folder inbox = store.getFolder("INBOX");
      inbox.open(Folder.READ_ONLY);
      int messageCount = inbox.getMessageCount();
      if (messageCount > 0) {
        return inbox.getMessage(messageCount);
      }
      inbox.close(false);
      store.close();
      return null;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static List<String> getSenders(Message message) {
    try {
      return Arrays.stream(message.getFrom()).map(Address::toString).toList();
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  public static List<String> getReceivers(Message message) {
    try {
      return Arrays.stream(message.getAllRecipients()).map(Address::toString).toList();
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  public static String getPlainTextBody(Message message) {
    try {
      return message.getContent().toString().trim();
    } catch (MessagingException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String getSubject(Message message) {
    try {
      return message.getSubject();
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  public static void sendEmail(String host, String port, String subject, String body, String to) {
    Properties properties = new Properties();
    properties.put("mail.smtp.host", host);
    properties.put("mail.smtp.port", port);
    properties.put("mail.smtp.auth", "true");
    properties.put("mail.smtp.ssl.enable", "false");

    Session session = Session.getDefaultInstance(properties);
    try {
      Message message = new MimeMessage(session);
      message.setFrom(new InternetAddress("any@test.com"));
      message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
      message.setSubject(subject);
      message.setText(body);

      try (Transport transport = session.getTransport()) {
        transport.connect("address@example.org", "pass");
        transport.sendMessage(message, message.getAllRecipients());
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }
}
