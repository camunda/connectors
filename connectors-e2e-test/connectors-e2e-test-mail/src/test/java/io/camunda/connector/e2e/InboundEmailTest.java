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

import static io.camunda.connector.e2e.BpmnFile.replace;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult;
import io.camunda.connector.runtime.inbound.state.ProcessStateStore;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.exception.OperateException;
import io.camunda.operate.model.ProcessDefinition;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import jakarta.mail.Flags;
import jakarta.mail.MessagingException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
public class InboundEmailTest extends BaseEmailTest {

  private static final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor();
  private static AtomicLong counter = new AtomicLong(1);
  @Autowired ProcessStateStore processStateStore;
  @Autowired CamundaOperateClient camundaOperateClient;
  @Mock private ProcessDefinition processDef;
  @Autowired private ZeebeClient zeebeClient;

  @BeforeEach
  public void beforeEach() {
    super.reset();
  }

  @Test
  public void shouldReceiveEmailAndSetAsSeen() throws OperateException, MessagingException {
    var model =
        replace(
            "email-inbound-connector-intermediate_unseen_read.bpmn",
            BpmnFile.Replace.replace("55555", super.getUnsecureImapPort()));

    mockProcessDefinition(model);

    scheduler.schedule(
        () -> super.sendEmail("test@camunda.com", "test", "hey"), 2, TimeUnit.SECONDS);

    processStateStore.update(
        new ProcessImportResult(
            Map.of(
                new ProcessImportResult.ProcessDefinitionIdentifier(
                    processDef.getBpmnProcessId(), processDef.getTenantId()),
                new ProcessImportResult.ProcessDefinitionVersion(
                    processDef.getKey(), processDef.getVersion().intValue()))));
    var bpmnTest = ZeebeTest.with(zeebeClient).deploy(model).createInstance();

    bpmnTest = bpmnTest.waitForProcessCompletion();

    Assertions.assertTrue(
        Arrays.stream(super.getLastReceivedEmails())
            .findFirst()
            .get()
            .getFlags()
            .contains(Flags.Flag.SEEN));
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("subject", "test");
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("plainTextBody", "hey");
  }

  @Test
  public void shouldThrowWhenAllMessageAreSeen() throws OperateException, MessagingException {
    var model =
        replace(
            "email-inbound-connector-intermediate_unseen_read.bpmn",
            BpmnFile.Replace.replace("55555", super.getUnsecureImapPort()));

    mockProcessDefinition(model);

    super.sendEmail("test@camunda.com", "test", "hey");

    Arrays.stream(getLastReceivedEmails()).findFirst().get().setFlag(Flags.Flag.SEEN, true);

    processStateStore.update(
        new ProcessImportResult(
            Map.of(
                new ProcessImportResult.ProcessDefinitionIdentifier(
                    processDef.getBpmnProcessId(), processDef.getTenantId()),
                new ProcessImportResult.ProcessDefinitionVersion(
                    processDef.getKey(), processDef.getVersion().intValue()))));
    var bpmnTest = ZeebeTest.with(zeebeClient).deploy(model).createInstance();

    Assertions.assertThrows(ConditionTimeoutException.class, bpmnTest::waitForProcessCompletion);
  }

  @Test
  public void shouldReceiveEmailAndDelete() throws OperateException, MessagingException {
    var model =
        replace(
            "email-inbound-connector-intermediate_unseen_delete.bpmn",
            BpmnFile.Replace.replace("55555", super.getUnsecureImapPort()));

    mockProcessDefinition(model);

    scheduler.schedule(
        () -> super.sendEmail("test@camunda.com", "test", "hey"), 2, TimeUnit.SECONDS);

    processStateStore.update(
        new ProcessImportResult(
            Map.of(
                new ProcessImportResult.ProcessDefinitionIdentifier(
                    processDef.getBpmnProcessId(), processDef.getTenantId()),
                new ProcessImportResult.ProcessDefinitionVersion(
                    processDef.getKey(), processDef.getVersion().intValue()))));

    var bpmnTest = ZeebeTest.with(zeebeClient).deploy(model).createInstance();

    bpmnTest = bpmnTest.waitForProcessCompletion();

    Assertions.assertTrue(
        Arrays.stream(super.getLastReceivedEmails())
            .findFirst()
            .get()
            .getFlags()
            .contains(Flags.Flag.DELETED));
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("subject", "test");
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("plainTextBody", "hey");
  }

  @Test
  public void shouldReceiveEmailAndMove() throws OperateException, MessagingException {
    var model =
        replace(
            "email-inbound-connector-intermediate_unseen_move.bpmn",
            BpmnFile.Replace.replace("55555", super.getUnsecureImapPort()));

    mockProcessDefinition(model);

    scheduler.schedule(
        () -> super.sendEmail("test@camunda.com", "test", "hey"), 2, TimeUnit.SECONDS);

    processStateStore.update(
        new ProcessImportResult(
            Map.of(
                new ProcessImportResult.ProcessDefinitionIdentifier(
                    processDef.getBpmnProcessId(), processDef.getTenantId()),
                new ProcessImportResult.ProcessDefinitionVersion(
                    processDef.getKey(), processDef.getVersion().intValue()))));

    var bpmnTest = ZeebeTest.with(zeebeClient).deploy(model).createInstance();

    bpmnTest = bpmnTest.waitForProcessCompletion();

    Assertions.assertEquals(2, getLastReceivedEmails().length);
    Assertions.assertTrue(
        Arrays.stream(getLastReceivedEmails())
            .findFirst()
            .get()
            .getFlags()
            .contains(Flags.Flag.DELETED));
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("subject", "test");
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("plainTextBody", "hey");
  }

  private void mockProcessDefinition(BpmnModelInstance model) throws OperateException {
    when(camundaOperateClient.getProcessDefinitionModel(1L)).thenReturn(model);
    when(processDef.getKey()).thenReturn(1L);
    when(processDef.getTenantId()).thenReturn(zeebeClient.getConfiguration().getDefaultTenantId());
    when(processDef.getBpmnProcessId())
        .thenReturn(model.getModelElementsByType(Process.class).stream().findFirst().get().getId());
    when(processDef.getVersion()).thenReturn(counter.getAndIncrement());
  }

  @Test
  public void shouldPollEmailAndMove() throws OperateException, MessagingException {
    var model =
        replace(
            "email-inbound-connector-intermediate_all_delete.bpmn",
            BpmnFile.Replace.replace("55555", super.getUnsecureImapPort()));

    mockProcessDefinition(model);

    super.sendEmail("test@camunda.com", "test", "hey");

    Arrays.stream(super.getLastReceivedEmails()).findFirst().get().setFlag(Flags.Flag.SEEN, true);

    processStateStore.update(
        new ProcessImportResult(
            Map.of(
                new ProcessImportResult.ProcessDefinitionIdentifier(
                    processDef.getBpmnProcessId(), processDef.getTenantId()),
                new ProcessImportResult.ProcessDefinitionVersion(
                    processDef.getKey(), processDef.getVersion().intValue()))));

    var bpmnTest = ZeebeTest.with(zeebeClient).deploy(model).createInstance();

    bpmnTest = bpmnTest.waitForProcessCompletion();

    Assertions.assertTrue(
        Arrays.stream(super.getLastReceivedEmails())
            .findFirst()
            .get()
            .getFlags()
            .contains(Flags.Flag.DELETED));
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("subject", "test");
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("plainTextBody", "hey");
  }
}
