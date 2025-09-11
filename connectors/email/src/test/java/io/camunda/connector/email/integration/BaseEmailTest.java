/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.integration;

import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import jakarta.mail.*;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

public class BaseEmailTest {

  protected static final String LOCALHOST = "localhost";
  private static final GreenMail greenMail = new GreenMail();
  @TempDir File tempDir;
  private GreenMailUser greenMailUser = greenMail.setUser("test@camunda.com", "password");

  @BeforeAll
  static void setup() {
    greenMail.start();
  }

  @AfterAll
  static void tearDown() {
    greenMail.stop();
  }

  protected static String getBodyAsHtml(Message message) {
    try {
      if (message.getContent() instanceof Multipart multipart) {
        for (int i = 0; i < multipart.getCount(); i++) {
          MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
          if (bodyPart.isMimeType("text/html")) {
            return (String) bodyPart.getContent();
          }
        }
      }
      return null;
    } catch (MessagingException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static byte[] getBodyAsByteArray(Message message) {
    try {
      if (message.getContent() instanceof Multipart multipart) {
        for (int i = 0; i < multipart.getCount(); i++) {
          MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
          if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
            return bodyPart.getInputStream().readAllBytes();
          }
        }
      }
      return null;
    } catch (MessagingException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static String getEmailSubject(Message message) {
    try {
      return message.getSubject();
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  protected Message[] getLastReceivedEmails() {
    return greenMail.getReceivedMessages();
  }

  protected void sendEmail(String to, String subject, String body) {
    MimeMessage mimeMessage =
        GreenMailUtil.createTextEmail(
            to, "test@camunda.com", subject, body, greenMail.getImap().getServerSetup());
    greenMailUser.deliver(mimeMessage);
  }

  protected String getUnsecurePop3Port() {
    return String.valueOf(greenMail.getPop3().getPort());
  }

  protected String getUnsecureImapPort() {
    return String.valueOf(greenMail.getImap().getPort());
  }

  protected String getUnsecureSmtpPort() {
    return String.valueOf(greenMail.getSmtp().getPort());
  }

  protected void reset() {
    try {
      greenMail.purgeEmailFromAllMailboxes();
    } catch (FolderException e) {
      throw new RuntimeException(e);
    }
  }

  protected String getBodyAsText(Message message) {
    try {
      if (message.getContent() instanceof Multipart multipart) {
        for (int i = 0; i < multipart.getCount(); i++) {
          MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
          if (bodyPart.isMimeType("text/plain")) {
            return (String) bodyPart.getContent();
          }
        }
      }
      return null;
    } catch (MessagingException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected List<String> getTo(Message message) {
    try {
      return Arrays.stream(message.getAllRecipients()).map(Address::toString).toList();
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  protected String getLastMessageId() {
    Message message = getLastReceivedEmails()[0];
    try {
      return message.getHeader("Message-ID")[0];
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }
}
