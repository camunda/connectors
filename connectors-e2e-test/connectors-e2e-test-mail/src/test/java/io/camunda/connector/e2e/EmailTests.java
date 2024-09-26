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

import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.e2e.helper.EmailTestHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import jakarta.mail.Message;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ZeebeSpringTest
@ExtendWith(MockitoExtension.class)
public class EmailTests extends BaseEmailTest {

  private static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/email/element-templates/email-outbound-connector.json";

  private static final String RESULT_EXPRESSION_SEND_EMAIL = "={sent: sent}";
  private static final String RESULT_VARIABLE_LIST_EMAIL = "messageList";
  private static final String RESULT_EXPRESSION_LIST_EMAIL =
      "={subject1: response[1].subject, subject2 : response[2].subject }";

  private static BpmnModelInstance getBpmnModelInstance(final String serviceTaskName) {
    return Bpmn.createProcess()
        .executable()
        .startEvent()
        .serviceTask(serviceTaskName)
        .endEvent()
        .done();
  }

  @Test
  public void shouldSendSMTPEmail() {
    File elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "simple")
            .property("authentication.simpleAuthenticationUsername", "username")
            .property("authentication.simpleAuthenticationPassword", "password")
            .property("protocol", "smtp")
            .property("data.smtpPort", String.valueOf(getSmtpInbucketPort()))
            .property("data.smtpHost", getSmtpInbucketHost())
            .property("smtpCryptographicProtocol", "NONE")
            .property("data.smtpActionDiscriminator", "sendEmailSmtp")
            .property("smtpFrom", "sender@test.com")
            .property("smtpTo", "receiver@test.com")
            .property("smtpSubject", "subject")
            .property("smtpBody", "content")
            .property("resultExpression", RESULT_EXPRESSION_SEND_EMAIL)
            .writeTo(new File(tempDir, "template.json"));

    BpmnModelInstance model = getBpmnModelInstance("sendEmailTask");
    BpmnModelInstance updatedModel = getBpmnModelInstance(model, elementTemplate, "sendEmailTask");
    var result = getZeebeTest(updatedModel);

    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceEvent()).hasVariableWithValue("sent", true);

    Message message =
        EmailTestHelper.getLastEmailFromInbucket(
            getPop3Host(), String.valueOf(getPop3Port()), "receiver");

    assertThat(message).isNotNull();
    assertThat(EmailTestHelper.getSenders(message)).hasSize(1).first().isEqualTo("sender@test.com");
    assertThat(EmailTestHelper.getReceivers(message))
        .hasSize(1)
        .first()
        .isEqualTo("receiver@test.com");
    assertThat(EmailTestHelper.getSubject(message)).isEqualTo("subject");
    assertThat(EmailTestHelper.getPlainTextBody(message)).isEqualTo("content");
  }

  @Test
  public void shouldListPop3Email() throws InterruptedException {

    File elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "simple")
            .property("authentication.simpleAuthenticationUsername", "receiver")
            .property("authentication.simpleAuthenticationPassword", "password")
            .property("protocol", "pop3")
            .property("data.pop3Port", String.valueOf(getPop3Port()))
            .property("data.pop3Host", getPop3Host())
            .property("pop3CryptographicProtocol", "NONE")
            .property("data.pop3ActionDiscriminator", "listEmailsPop3")
            .property("pop3maxToBeRead", "100")
            .property("pop3SortField", "SENT_DATE")
            .property("pop3SortOrder", "DESC")
            .property("resultExpression", RESULT_EXPRESSION_LIST_EMAIL)
            .writeTo(new File(tempDir, "template.json"));

    EmailTestHelper.sendEmail(
        getSmtpInbucketHost(),
        String.valueOf(getSmtpInbucketPort()),
        "subject1",
        "body1",
        "receiver@test.com");

    // To verify the sorting as only seconds are used
    Thread.sleep(1000);

    EmailTestHelper.sendEmail(
        getSmtpInbucketHost(),
        String.valueOf(getSmtpInbucketPort()),
        "subject2",
        "body2",
        "receiver@test.com");

    BpmnModelInstance model = getBpmnModelInstance("listEmailsTask");
    BpmnModelInstance updatedModel = getBpmnModelInstance(model, elementTemplate, "listEmailsTask");

    var result = getZeebeTest(updatedModel);

    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceEvent()).hasVariableWithValue("subject1", "subject2");
    assertThat(result.getProcessInstanceEvent()).hasVariableWithValue("subject2", "subject1");
    assertThat(result.getProcessInstanceEvent());
  }

  @Test
  public void shouldListImapEmail() throws InterruptedException {

    File elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "simple")
            .property("authentication.simpleAuthenticationUsername", "address@example.org")
            .property("authentication.simpleAuthenticationPassword", "pass")
            .property("protocol", "imap")
            .property("data.imapPort", String.valueOf(getImapPort()))
            .property("data.imapHost", getImapHost())
            .property("imapCryptographicProtocol", "SSL")
            .property("data.imapActionDiscriminator", "listEmailsImap")
            .property("imapMaxToBeRead", "100")
            .property("imapSortField", "RECEIVED_DATE")
            .property("imapSortOrder", "DESC")
            .property("resultExpression", RESULT_EXPRESSION_LIST_EMAIL)
            .writeTo(new File(tempDir, "template.json"));

    EmailTestHelper.sendEmail(
        getSmtpDevelHost(),
        String.valueOf(getSmtpDevelPort()),
        "subject1",
        "body1",
        "address@example.org");

    // To verify the sorting as only seconds are used
    Thread.sleep(1000);

    EmailTestHelper.sendEmail(
        getSmtpDevelHost(),
        String.valueOf(getSmtpDevelPort()),
        "subject2",
        "body2",
        "address@example.org");

    BpmnModelInstance model = getBpmnModelInstance("listEmailsTask");
    BpmnModelInstance updatedModel = getBpmnModelInstance(model, elementTemplate, "listEmailsTask");

    var result = getZeebeTest(updatedModel);

    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceEvent()).hasVariableWithValue("subject1", "subject2");
    assertThat(result.getProcessInstanceEvent()).hasVariableWithValue("subject2", "subject1");
    assertThat(result.getProcessInstanceEvent());
  }

  private ZeebeTest getZeebeTest(final BpmnModelInstance updatedModel) {
    return ZeebeTest.with(zeebeClient)
        .deploy(updatedModel)
        .createInstance()
        .waitForProcessCompletion();
  }

  private BpmnModelInstance getBpmnModelInstance(
      final BpmnModelInstance model, final File elementTemplate, final String taskName) {
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, "test.bpmn"))
        .apply(elementTemplate, taskName, new File(tempDir, "result.bpmn"));
  }
}
