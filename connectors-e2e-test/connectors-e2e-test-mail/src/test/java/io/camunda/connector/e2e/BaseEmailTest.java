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

import com.icegreen.greenmail.util.GreenMail;
import io.camunda.zeebe.client.ZeebeClient;
import jakarta.mail.Message;
import java.io.File;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

public class BaseEmailTest {

  protected static final String LOCALHOST = "localhost";
  private static final GreenMail greenMail = new GreenMail();
  @TempDir File tempDir;

  @Autowired ZeebeClient zeebeClient;

  @BeforeAll
  static void setup() {
    greenMail.setUser("test@camunda.com", "password");
    greenMail.start();
  }

  @AfterAll
  static void tearDown() {
    greenMail.stop();
  }

  protected Message[] getLastReceivedEmails() {
    return greenMail.getReceivedMessages();
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
}
