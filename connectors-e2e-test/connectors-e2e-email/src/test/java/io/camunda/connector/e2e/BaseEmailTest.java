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
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class BaseEmailTest {

  protected static final String LOCALHOST = "localhost";
  private static final GreenMail greenMail = new GreenMail();
  private static final GreenMailUser greenMailUser =
      greenMail.setUser("test@camunda.com", "password");

  @BeforeAll
  public static void setup() {
    greenMail.start();
  }

  @AfterAll
  public static void tearDown() {
    greenMail.stop();
  }

  protected Message[] getLastReceivedEmails() {
    return greenMail.getReceivedMessages();
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

  protected void replyTo(Message[] lastReceivedEmails) {
    for (Message message : lastReceivedEmails) {
      try {
        MimeMessage mimeMessage =
            GreenMailUtil.createTextEmail(
                "test@camunda.com",
                "test@camunda.com",
                "any",
                "test",
                greenMail.getImap().getServerSetup());
        mimeMessage.setHeader("In-Reply-To", message.getHeader("Message-ID")[0]);
        greenMailUser.deliver(mimeMessage);
      } catch (MessagingException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
