/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e;

import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
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

  protected static List<String> getSenders(Message message) {
    try {
      return Arrays.stream(message.getFrom()).map(Address::toString).toList();
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  protected static List<String> getReceivers(Message message) {
    try {
      return Arrays.stream(message.getAllRecipients()).map(Address::toString).toList();
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  protected static String getPlainTextBody(Message message) {
    try {
      if (message.getContent() instanceof Multipart multipart) {
        return multipart.getBodyPart(0).getContent().toString();
      }
      return null;
    } catch (MessagingException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static String getSubject(Message message) {
    try {
      return message.getSubject();
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  protected Message[] getLastReceivedEmails() {
    return greenMail.getReceivedMessages();
  }

  protected boolean waitForNewEmails(long timeout, int numberOfEmails) {
    return greenMail.waitForIncomingEmail(timeout, numberOfEmails);
  }

  protected void sendEmail(String to, String subject, String body) {
    MimeMessage mimeMessage =
        GreenMailUtil.createTextEmail(
            to, "test@camunda.com", subject, body, greenMail.getImap().getServerSetup());
    greenMailUser.deliver(mimeMessage);
  }

  protected String getLastEmailMessageId() {
    Message message = getLastReceivedEmails()[0];
    try {
      String messageId = message.getHeader("Message-ID")[0];
      return messageId.trim().replaceAll("[<>]", "");
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
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
}
