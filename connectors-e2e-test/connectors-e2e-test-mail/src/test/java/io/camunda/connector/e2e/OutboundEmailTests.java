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

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import jakarta.mail.Message;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@ExtendWith(MockitoExtension.class)
public class OutboundEmailTests extends BaseEmailTest {

  private static final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor();

  private static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/email/element-templates/email-outbound-connector.json";
  private static final String RESULT_EXPRESSION_SEND_EMAIL = "={sent: sent}";
  private static final String RESULT_EXPRESSION_LIST_EMAIL =
      "={subject1: response[1].subject, subject2 : response[2].subject }";
  private static final String RESULT_EXPRESSION_DELETE_EMAIL =
      "={deleted : deleted, messageId : messageId }";
  private static final String RESULT_EXPRESSION_READ_EMAIL =
      "={fromAddress : fromAddress, messageId : messageId, subject: subject, size: size, plainTextBody : plainTextBody }";
  private static final String RESULT_EXPRESSION_MOVE_EMAIL =
      "={messageId : messageId, from: from, to: to }";
  @Autowired private ZeebeClient zeebeClient;

  private static BpmnModelInstance getBpmnModelInstance(final String serviceTaskName) {
    return Bpmn.createProcess()
        .executable()
        .startEvent()
        .serviceTask(serviceTaskName)
        .endEvent()
        .done();
  }

  @BeforeEach
  public void beforeEach() {
    super.reset();
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(10));
  }

  @Test
  public void shouldSendSMTPTextEmail() {
    File elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "simple")
            .property("authentication.simpleAuthenticationUsername", "test@camunda.com")
            .property("authentication.simpleAuthenticationPassword", "password")
            .property("protocol", "smtp")
            .property("data.smtpPort", getUnsecureSmtpPort())
            .property("data.smtpHost", LOCALHOST)
            .property("smtpCryptographicProtocol", "NONE")
            .property("data.smtpActionDiscriminator", "sendEmailSmtp")
            .property("smtpFrom", "test@camunda.com")
            .property("smtpTo", "receiver@test.com")
            .property("smtpSubject", "subject")
            .property("contentType", "PLAIN")
            .property("smtpBody", "content")
            .property("resultExpression", RESULT_EXPRESSION_SEND_EMAIL)
            .writeTo(new File(tempDir, "template.json"));

    BpmnModelInstance model = getBpmnModelInstance("sendEmailTask");
    BpmnModelInstance updatedModel = getBpmnModelInstance(model, elementTemplate, "sendEmailTask");
    var result = getZeebeTest(updatedModel);

    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceEvent()).hasVariable("sent", true);

    assertTrue(super.waitForNewEmails(5000, 1));
    List<Message> message = List.of(super.getLastReceivedEmails());
    assertThat(message).isNotNull();
    assertThat(getSenders(message.getFirst())).hasSize(1).first().isEqualTo("test@camunda.com");
    assertThat(getReceivers(message.getFirst())).hasSize(1).first().isEqualTo("receiver@test.com");
    assertThat(getSubject(message.getFirst())).isEqualTo("subject");
    assertThat(getPlainTextBody(message.getFirst())).isEqualTo("content");
  }

  @Test
  public void shouldSendSMTPHtmlEmail() {
    File elementTemplate =
            ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
                    .property("authentication.type", "simple")
                    .property("authentication.simpleAuthenticationUsername", "test@camunda.com")
                    .property("authentication.simpleAuthenticationPassword", "password")
                    .property("protocol", "smtp")
                    .property("data.smtpPort", getUnsecureSmtpPort())
                    .property("data.smtpHost", LOCALHOST)
                    .property("smtpCryptographicProtocol", "NONE")
                    .property("data.smtpActionDiscriminator", "sendEmailSmtp")
                    .property("smtpFrom", "test@camunda.com")
                    .property("smtpTo", "receiver@test.com")
                    .property("smtpSubject", "subject")
                    .property("contentType", "HTML")
                    .property("smtpHtmlBody", "<h1>content</h1>")
                    .property("resultExpression", RESULT_EXPRESSION_SEND_EMAIL)
                    .writeTo(new File(tempDir, "template.json"));

    BpmnModelInstance model = getBpmnModelInstance("sendEmailTask");
    BpmnModelInstance updatedModel = getBpmnModelInstance(model, elementTemplate, "sendEmailTask");
    var result = getZeebeTest(updatedModel);

    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceEvent()).hasVariableWithValue("sent", true);

    assertTrue(super.waitForNewEmails(5000, 1));
    List<Message> message = List.of(super.getLastReceivedEmails());
    assertThat(message).isNotNull();
    assertThat(getSenders(message.getFirst())).hasSize(1).first().isEqualTo("test@camunda.com");
    assertThat(getReceivers(message.getFirst())).hasSize(1).first().isEqualTo("receiver@test.com");
    assertThat(getSubject(message.getFirst())).isEqualTo("subject");
    assertThat(getHtmlBody(message.getFirst())).isEqualTo("<h1>content</h1>");
  }

  @Test
  public void shouldListPop3Email() {

    File elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "simple")
            .property("authentication.simpleAuthenticationUsername", "test@camunda.com")
            .property("authentication.simpleAuthenticationPassword", "password")
            .property("protocol", "pop3")
            .property("data.pop3Port", getUnsecurePop3Port())
            .property("data.pop3Host", LOCALHOST)
            .property("pop3CryptographicProtocol", "NONE")
            .property("data.pop3ActionDiscriminator", "listEmailsPop3")
            .property("pop3maxToBeRead", "100")
            .property("pop3SortField", "SENT_DATE")
            .property("pop3SortOrder", "DESC")
            .property("resultExpression", RESULT_EXPRESSION_LIST_EMAIL)
            .writeTo(new File(tempDir, "template.json"));

    scheduler.schedule(
        () -> super.sendEmail("receiver@test.com", "subject1", "content1"), 1, SECONDS);
    scheduler.schedule(
        () -> super.sendEmail("receiver@test.com", "subject2", "content2"), 2, SECONDS);

    BpmnModelInstance model = getBpmnModelInstance("listEmailsTask");
    BpmnModelInstance updatedModel = getBpmnModelInstance(model, elementTemplate, "listEmailsTask");
    await().atMost(3, SECONDS).until(() -> super.getLastReceivedEmails().length == 2);
    var result = getZeebeTest(updatedModel);
    assertThat(result.getProcessInstanceEvent()).hasVariable("subject1", "subject2");
    assertThat(result.getProcessInstanceEvent()).hasVariable("subject2", "subject1");
  }

  @Test
  public void shouldListImapEmail() {

    File elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "simple")
            .property("authentication.simpleAuthenticationUsername", "test@camunda.com")
            .property("authentication.simpleAuthenticationPassword", "password")
            .property("protocol", "imap")
            .property("data.imapPort", getUnsecureImapPort())
            .property("data.imapHost", LOCALHOST)
            .property("imapCryptographicProtocol", "NONE")
            .property("data.imapActionDiscriminator", "listEmailsImap")
            .property("imapMaxToBeRead", "100")
            .property("imapSortField", "RECEIVED_DATE")
            .property("imapSortOrder", "DESC")
            .property("resultExpression", RESULT_EXPRESSION_LIST_EMAIL)
            .writeTo(new File(tempDir, "template.json"));

    scheduler.schedule(
        () -> super.sendEmail("receiver@test.com", "subject1", "content1"), 1, SECONDS);
    scheduler.schedule(
        () -> super.sendEmail("receiver@test.com", "subject2", "content2"), 2, SECONDS);

    BpmnModelInstance model = getBpmnModelInstance("listEmailsTask");
    BpmnModelInstance updatedModel = getBpmnModelInstance(model, elementTemplate, "listEmailsTask");
    await().atMost(3, SECONDS).until(() -> super.getLastReceivedEmails().length == 2);
    var result = getZeebeTest(updatedModel);

    assertThat(result.getProcessInstanceEvent()).hasVariable("subject1", "subject2");
    assertThat(result.getProcessInstanceEvent()).hasVariable("subject2", "subject1");
  }

  @Test
  public void shouldDeletePop3Email() {

    super.sendEmail("test@camunda.com", "subject1", "content1");
    String messageId = getLastEmailMessageId();
    File elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "simple")
            .property("authentication.simpleAuthenticationUsername", "test@camunda.com")
            .property("authentication.simpleAuthenticationPassword", "password")
            .property("protocol", "pop3")
            .property("data.pop3Port", getUnsecurePop3Port())
            .property("data.pop3Host", LOCALHOST)
            .property("pop3CryptographicProtocol", "NONE")
            .property("data.pop3ActionDiscriminator", "deleteEmailPop3")
            .property("pop3MessageIdDelete", messageId)
            .property("resultExpression", RESULT_EXPRESSION_DELETE_EMAIL)
            .writeTo(new File(tempDir, "template.json"));

    BpmnModelInstance model = getBpmnModelInstance("deleteEmailTask");
    BpmnModelInstance updatedModel =
        getBpmnModelInstance(model, elementTemplate, "deleteEmailTask");
    var result = getZeebeTest(updatedModel);
    assertThat(result.getProcessInstanceEvent()).hasVariable("messageId", messageId);
    assertThat(result.getProcessInstanceEvent()).hasVariable("deleted", true);
  }

  @Test
  public void shouldDeleteImapEmail() {

    super.sendEmail("test@camunda.com", "subject1", "content1");
    String messageId = getLastEmailMessageId();
    File elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "simple")
            .property("authentication.simpleAuthenticationUsername", "test@camunda.com")
            .property("authentication.simpleAuthenticationPassword", "password")
            .property("protocol", "imap")
            .property("data.imapPort", getUnsecureImapPort())
            .property("data.imapHost", LOCALHOST)
            .property("imapCryptographicProtocol", "NONE")
            .property("data.imapActionDiscriminator", "deleteEmailImap")
            .property("imapMessageIdDelete", messageId)
            .property("deleteEmailFolder", "INBOX")
            .property("resultExpression", RESULT_EXPRESSION_DELETE_EMAIL)
            .writeTo(new File(tempDir, "template.json"));

    BpmnModelInstance model = getBpmnModelInstance("deleteEmailTask");
    BpmnModelInstance updatedModel =
        getBpmnModelInstance(model, elementTemplate, "deleteEmailTask");
    var result = getZeebeTest(updatedModel);
    assertThat(result.getProcessInstanceEvent()).hasVariable("messageId", messageId);
    assertThat(result.getProcessInstanceEvent()).hasVariable("deleted", true);
  }

  @Test
  public void shouldReadPop3Email() {

    super.sendEmail("test@camunda.com", "subject", "content");
    String messageId = getLastEmailMessageId();
    File elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "simple")
            .property("authentication.simpleAuthenticationUsername", "test@camunda.com")
            .property("authentication.simpleAuthenticationPassword", "password")
            .property("protocol", "pop3")
            .property("data.pop3Port", getUnsecurePop3Port())
            .property("data.pop3Host", LOCALHOST)
            .property("pop3CryptographicProtocol", "NONE")
            .property("data.pop3ActionDiscriminator", "readEmailPop3")
            .property("pop3MessageIdRead", messageId)
            .property("resultExpression", RESULT_EXPRESSION_READ_EMAIL)
            .writeTo(new File(tempDir, "template.json"));

    BpmnModelInstance model = getBpmnModelInstance("readEmailTask");
    BpmnModelInstance updatedModel = getBpmnModelInstance(model, elementTemplate, "readEmailTask");
    var result = getZeebeTest(updatedModel);
    assertThat(result.getProcessInstanceEvent()).hasVariable("messageId", messageId);
    assertThat(result.getProcessInstanceEvent()).hasVariable("fromAddress", "test@camunda.com");
    assertThat(result.getProcessInstanceEvent()).hasVariable("subject", "subject");
    assertThat(result.getProcessInstanceEvent()).hasVariable("size", 9);
    assertThat(result.getProcessInstanceEvent()).hasVariable("plainTextBody", "content\r\n");
  }

  @Test
  public void shouldReadImapEmail() {

    super.sendEmail("test@camunda.com", "subject", "content");
    String messageId = getLastEmailMessageId();
    File elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "simple")
            .property("authentication.simpleAuthenticationUsername", "test@camunda.com")
            .property("authentication.simpleAuthenticationPassword", "password")
            .property("protocol", "imap")
            .property("data.imapPort", getUnsecureImapPort())
            .property("data.imapHost", LOCALHOST)
            .property("imapCryptographicProtocol", "NONE")
            .property("data.imapActionDiscriminator", "readEmailImap")
            .property("imapMessageIdRead", messageId)
            .property("readEmailFolder", "INBOX")
            .property("resultExpression", RESULT_EXPRESSION_READ_EMAIL)
            .writeTo(new File(tempDir, "template.json"));

    BpmnModelInstance model = getBpmnModelInstance("readEmailTask");
    BpmnModelInstance updatedModel = getBpmnModelInstance(model, elementTemplate, "readEmailTask");
    var result = getZeebeTest(updatedModel);
    assertThat(result.getProcessInstanceEvent()).hasVariable("messageId", messageId);
    assertThat(result.getProcessInstanceEvent()).hasVariable("fromAddress", "test@camunda.com");
    assertThat(result.getProcessInstanceEvent()).hasVariable("subject", "subject");
    assertThat(result.getProcessInstanceEvent()).hasVariable("size", 7);
    assertThat(result.getProcessInstanceEvent()).hasVariable("plainTextBody", "content");
  }

  @Test
  public void shouldMoveImapEmail() {

    super.sendEmail("test@camunda.com", "subject", "content");
    String messageId = getLastEmailMessageId();
    File elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "simple")
            .property("authentication.simpleAuthenticationUsername", "test@camunda.com")
            .property("authentication.simpleAuthenticationPassword", "password")
            .property("protocol", "imap")
            .property("data.imapPort", getUnsecureImapPort())
            .property("data.imapHost", LOCALHOST)
            .property("imapCryptographicProtocol", "NONE")
            .property("data.imapActionDiscriminator", "moveEmailImap")
            .property("imapMessageIdMove", messageId)
            .property("data.fromFolder", "INBOX")
            .property("data.toFolder", "TEST")
            .property("resultExpression", RESULT_EXPRESSION_MOVE_EMAIL)
            .writeTo(new File(tempDir, "template.json"));

    BpmnModelInstance model = getBpmnModelInstance("readEmailTask");
    BpmnModelInstance updatedModel = getBpmnModelInstance(model, elementTemplate, "readEmailTask");
    var result = getZeebeTest(updatedModel);

    assertThat(result.getProcessInstanceEvent()).hasVariable("messageId", messageId);
    assertThat(result.getProcessInstanceEvent()).hasVariable("from", "INBOX");
    assertThat(result.getProcessInstanceEvent()).hasVariable("to", "TEST");
  }

  private BpmnModelInstance getBpmnModelInstance(
      final BpmnModelInstance model, final File elementTemplate, final String taskName) {
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, "test.bpmn"))
        .apply(elementTemplate, taskName, new File(tempDir, "result.bpmn"));
  }

  protected ZeebeTest getZeebeTest(final BpmnModelInstance updatedModel) {
    return ZeebeTest.with(zeebeClient)
        .deploy(updatedModel)
        .createInstance()
        .waitForProcessCompletion();
  }
}
