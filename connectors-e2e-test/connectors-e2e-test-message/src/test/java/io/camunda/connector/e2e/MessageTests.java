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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false",
      "camunda.saas.secrets.projectId=42",
      "camunda.client.enabled=true",
      "camunda.connector.auth.audience=connectors.dev.ultrawombat.com",
      "camunda.connector.cloud.organizationId=orgId",
      "camunda.connector.auth.console.audience=cloud.dev.ultrawombat.com",
      "camunda.connector.auth.issuer=https://weblogin.cloud.dev.ultrawombat.com/",
      "camunda.connector.secretprovider.discovery.enabled=false",
      "management.endpoints.web.exposure.include=*"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@SlowTest
@ExtendWith(MockitoExtension.class)
@ComponentScan(basePackages = {"io.camunda.connector.runtime.app"})
public class MessageTests {

  private static final String MESSAGE_NAME = "first-message";
  private static final String CORRELATION_KEY_EXPRESSION = "valOneCorrelation";
  private static final String CORRELATION_VALUE = "123";

  @TempDir File tempDir;

  @Autowired CamundaClient camundaClient;
  private ProcessInstanceEvent receiveInstance;
  private BpmnModelInstance publishMessageModel;

  @BeforeEach
  public void createReceiveInstance() {
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("receiveProcess")
                .startEvent()
                .intermediateCatchEvent()
                .message(
                    m ->
                        m.name(MESSAGE_NAME)
                            .zeebeCorrelationKeyExpression(CORRELATION_KEY_EXPRESSION))
                .endEvent()
                .done(),
            "receiveMessage.bpmn")
        .send()
        .join();

    receiveInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("receiveProcess")
            .latestVersion()
            .variable(CORRELATION_KEY_EXPRESSION, CORRELATION_VALUE)
            .send()
            .join();

    publishMessageModel = createSendTaskModel();
  }

  @Test
  public void testPublishMessage() {
    var sendTaskTemplate =
        createSendTaskTemplate(
            "publish", "={messageKeyResult: messageKey, tenantIdResult: tenantId}");

    BpmnModelInstance messageModelWithElTemplate =
        new BpmnFile(publishMessageModel)
            .writeToFile(new File(tempDir, "message_send.bpmn"))
            .apply(
                sendTaskTemplate,
                "sendTask",
                new File(tempDir, "message_send_with_el_template.bpmn"));

    ProcessInstanceEvent processInstance =
        ZeebeTest.with(camundaClient)
            .deploy(messageModelWithElTemplate)
            .createInstance()
            .waitForProcessCompletion()
            .getProcessInstanceEvent();

    assertThat(processInstance)
        .hasVariableNames("messageResponse", "messageKeyResult", "tenantIdResult")
        .hasVariable("tenantIdResult", "<default>");

    assertThatReceiveInstanceIsEnded();
  }

  @Test
  @Disabled(
      """
Unauthorized access to correlate message REST API:
Details from surefire report:
2025-03-03T08:45:02.658+01:00 DEBUG 99400 --- [pool-7-thread-1] i.c.c.r.c.outbound.ConnectorJobHandler   : Exception while processing job: 2251799813685359 for tenant: <default>

io.camunda.zeebe.client.api.command.ProblemException: Failed with code 401: 'Unauthorized'. Details: 'class ProblemDetail {
    type: about:blank
    title: Unexpected server response
    status: 500
    detail: {"message":"Full authentication is required to access this resource"}
    instance: null
}'
    at io.camunda.zeebe.client.impl.http.ApiCallback.handleErrorResponse(ApiCallback.java:113)
    at io.camunda.zeebe.client.impl.http.ApiCallback.completed(ApiCallback.java:61)
    at io.camunda.zeebe.client.impl.http.ApiCallback.completed(ApiCallback.java:31)
    at org.apache.hc.core5.concurrent.BasicFuture.completed(BasicFuture.java:148)
    at org.apache.hc.core5.concurrent.ComplexFuture.completed(ComplexFuture.java:72)
    at org.apache.hc.client5.http.impl.async.InternalAbstractHttpAsyncClient$2$1.completed(InternalAbstractHttpAsyncClient.java:310)
    at org.apache.hc.core5.http.nio.support.AbstractAsyncResponseConsumer$1.completed(AbstractAsyncResponseConsumer.java:101)
    at org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer.completed(AbstractBinAsyncEntityConsumer.java:87)
    at org.apache.hc.core5.http.nio.entity.AbstractBinDataConsumer.streamEnd(AbstractBinDataConsumer.java:83)
    at org.apache.hc.core5.http.nio.support.AbstractAsyncResponseConsumer.streamEnd(AbstractAsyncResponseConsumer.java:142)
    at org.apache.hc.client5.http.impl.async.HttpAsyncMainClientExec$1.streamEnd(HttpAsyncMainClientExec.java:284)
    at org.apache.hc.core5.http.impl.nio.ClientHttp1StreamHandler.dataEnd(ClientHttp1StreamHandler.java:276)
    at org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexer.dataEnd(ClientHttp1StreamDuplexer.java:366)
    at org.apache.hc.core5.http.impl.nio.AbstractHttp1StreamDuplexer.onInput(AbstractHttp1StreamDuplexer.java:338)
    at org.apache.hc.core5.http.impl.nio.AbstractHttp1IOEventHandler.inputReady(AbstractHttp1IOEventHandler.java:64)
    at org.apache.hc.core5.http.impl.nio.ClientHttp1IOEventHandler.inputReady(ClientHttp1IOEventHandler.java:41)
    at org.apache.hc.core5.reactor.InternalDataChannel.onIOEvent(InternalDataChannel.java:143)
    at org.apache.hc.core5.reactor.InternalChannel.handleIOEvent(InternalChannel.java:51)
    at org.apache.hc.core5.reactor.SingleCoreIOReactor.processEvents(SingleCoreIOReactor.java:176)
    at org.apache.hc.core5.reactor.SingleCoreIOReactor.doExecute(SingleCoreIOReactor.java:125)
    at org.apache.hc.core5.reactor.AbstractSingleCoreIOReactor.execute(AbstractSingleCoreIOReactor.java:92)
    at org.apache.hc.core5.reactor.IOReactorWorker.run(IOReactorWorker.java:44)
    at java.base/java.lang.Thread.run(Thread.java:1583)
          """)
  public void testCorrelateMessageWithResult() {
    var sendTaskTemplate =
        createSendTaskTemplate(
            "correlate",
            "={messageKeyResult: messageKey, processInstanceKeyResult: processInstanceKey, tenantIdResult: tenantId}");

    BpmnModelInstance correlateModelWithElTemplate =
        new BpmnFile(publishMessageModel)
            .writeToFile(new File(tempDir, "message_correlate.bpmn"))
            .apply(
                sendTaskTemplate,
                "sendTask",
                new File(tempDir, "message_correlate_with_el_template.bpmn"));

    ProcessInstanceEvent processInstance =
        ZeebeTest.with(camundaClient)
            .deploy(correlateModelWithElTemplate)
            .createInstance()
            .waitForProcessCompletion()
            .getProcessInstanceEvent();

    assertThat(processInstance)
        .hasVariableNames(
            "messageResponse", "messageKeyResult", "tenantIdResult", "processInstanceKeyResult")
        .hasVariable("tenantIdResult", "<default>")
        .hasVariable(
            "processInstanceKeyResult", Long.toString(receiveInstance.getProcessInstanceKey()));

    assertThatReceiveInstanceIsEnded();
  }

  private BpmnModelInstance createSendTaskModel() {
    BpmnModelInstance publishMessageModel =
        Bpmn.createExecutableProcess()
            .startEvent()
            .sendTask("sendTask")
            .endEvent("sendEndEvent")
            .done();
    return publishMessageModel;
  }

  private File createSendTaskTemplate(String mode, String resultExpression) {
    var sendTaskTemplate =
        ElementTemplate.from(
                "../../connectors/camunda-message/element-templates/send-message-connector-send-task.json")
            .property("correlationType.type", mode)
            .property("messageName", MESSAGE_NAME)
            .property("correlationKey", CORRELATION_VALUE)
            .property("resultVariable", "messageResponse")
            .property("resultExpression", resultExpression)
            .writeTo(new File(tempDir, "sendTaskTemplate.json"));
    return sendTaskTemplate;
  }

  private void assertThatReceiveInstanceIsEnded() {
    Awaitility.with()
        .pollInSameThread()
        .await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> CamundaAssert.assertThat(receiveInstance).isCompleted());
  }
}
