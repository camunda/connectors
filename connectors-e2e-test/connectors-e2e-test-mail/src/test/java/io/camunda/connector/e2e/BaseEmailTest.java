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

import io.camunda.zeebe.client.ZeebeClient;
import java.io.File;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class BaseEmailTest {

  private static final String inbucketDockerImage = "inbucket/inbucket";
  private static final String imapDevelDockerComposeFile =
      "src/test/resources/docker-compose/docker-compose.yml";
  static GenericContainer<?> emailPOP3AndSMTPContainer;
  static DockerComposeContainer<?> emailIMAPContainer;
  @TempDir File tempDir;

  @Autowired ZeebeClient zeebeClient;

  @BeforeAll
  static void setup() {
    emailPOP3AndSMTPContainer =
        new GenericContainer<>(DockerImageName.parse(inbucketDockerImage))
            .withExposedPorts(2500, 1100, 9000);
    emailIMAPContainer =
        new DockerComposeContainer<>(new File(imapDevelDockerComposeFile))
            .withExposedService("imap", 993)
            .withExposedService("imap", 25)
            .withExposedService("imap", 143);
    emailIMAPContainer.start();
    emailPOP3AndSMTPContainer.start();
  }

  @AfterAll
  static void tearDown() {
    if (emailPOP3AndSMTPContainer != null) {
      emailPOP3AndSMTPContainer.stop();
    }
    if (emailIMAPContainer != null) {
      emailIMAPContainer.stop();
    }
  }

  public String getSmtpInbucketHost() {
    return emailPOP3AndSMTPContainer.getHost();
  }

  public Integer getSmtpInbucketPort() {
    return emailPOP3AndSMTPContainer.getMappedPort(2500);
  }

  public String getSmtpDevelHost() {
    return emailIMAPContainer.getServiceHost("imap", 25);
  }

  public Integer getSmtpDevelPort() {
    return emailIMAPContainer.getServicePort("imap", 25);
  }

  public String getPop3Host() {
    return emailPOP3AndSMTPContainer.getHost();
  }

  public Integer getPop3Port() {
    return emailPOP3AndSMTPContainer.getMappedPort(1100);
  }

  public String getImapHost() {
    return emailIMAPContainer.getServiceHost("imap", 993);
  }

  public Integer getImapPort() {
    return emailIMAPContainer.getServicePort("imap", 993);
  }
}
