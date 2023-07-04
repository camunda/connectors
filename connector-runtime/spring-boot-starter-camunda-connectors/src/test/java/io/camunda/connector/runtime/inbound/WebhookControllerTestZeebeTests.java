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
package io.camunda.connector.runtime.inbound;

import static io.camunda.connector.runtime.inbound.WebhookControllerPlainJavaTests.webhookProperties;
import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;
import static io.camunda.zeebe.spring.test.ZeebeTestThreadSupport.waitForProcessInstanceCompleted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingResult;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.impl.inbound.result.ProcessInstance;
import io.camunda.connector.impl.inbound.result.StartEventCorrelationResult;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.inbound.lifecycle.ActiveInboundConnector;
import io.camunda.connector.runtime.inbound.webhook.FeelExpressionErrorResponse;
import io.camunda.connector.runtime.inbound.webhook.InboundWebhookRestController;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.process.test.inspections.model.InspectedProcessInstance;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import java.util.HashMap;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=true"
    })
@ZeebeSpringTest
@ExtendWith(MockitoExtension.class)
class WebhookControllerTestZeebeTests {

  @Autowired private WebhookConnectorRegistry webhookConnectorRegistry;

  @Autowired private ZeebeClient zeebeClient;

  @Autowired private SecretProvider secretProvider;

  @Autowired private InboundCorrelationHandler correlationHandler;

  @Autowired @InjectMocks private InboundWebhookRestController controller;

  @BeforeEach
  public void beforeEach() {
    webhookConnectorRegistry.reset();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSuccessfulProcessingWithActivation() throws Exception {
    WebhookConnectorExecutable webhookConnectorExecutable = mock(WebhookConnectorExecutable.class);
    when(webhookConnectorExecutable.triggerWebhook(any(WebhookProcessingPayload.class)))
        .thenReturn(mock(WebhookProcessingResult.class));

    var webhookProperties = webhookProperties("processA", 1, "myPath");
    var webhookContext =
        new InboundConnectorContextImpl(
            secretProvider, v -> {}, webhookProperties, correlationHandler, (e) -> {});

    // Register webhook function 'implementation'
    webhookConnectorRegistry.register(
        new ActiveInboundConnector(webhookConnectorExecutable, webhookContext));

    deployProcess("processA");

    ResponseEntity<InboundConnectorResult<?>> responseEntity =
        (ResponseEntity<InboundConnectorResult<?>>)
            controller.inbound(
                "myPath",
                new HashMap<>(),
                "{}".getBytes(),
                new HashMap<>(),
                new MockHttpServletRequest());

    assertEquals(200, responseEntity.getStatusCode().value());
    assertTrue(Objects.requireNonNull(responseEntity.getBody()).isActivated());

    var result = responseEntity.getBody();
    assertInstanceOf(StartEventCorrelationResult.class, result);
    ProcessInstance processInstance =
        ((StartEventCorrelationResult) result).getResponseData().get();

    waitForProcessInstanceCompleted(processInstance.getProcessInstanceKey());
    assertThat(new InspectedProcessInstance(processInstance.getProcessInstanceKey())).isCompleted();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSuccessfulProcessingWithFailedActivation() throws Exception {
    WebhookConnectorExecutable webhookConnectorExecutable = mock(WebhookConnectorExecutable.class);
    when(webhookConnectorExecutable.triggerWebhook(any(WebhookProcessingPayload.class)))
        .thenReturn(mock(WebhookProcessingResult.class));

    var correlationHandlerMock = mock(InboundCorrelationHandler.class);
    var correlationResultMock = mock(InboundConnectorResult.class);
    when(correlationResultMock.isActivated()).thenReturn(false);
    when(correlationHandlerMock.correlate(any(), any(), any())).thenReturn(correlationResultMock);

    var webhookProperties = webhookProperties("nonExistingProcess", 1, "myPath");
    var webhookContext =
        new InboundConnectorContextImpl(
            secretProvider, v -> {}, webhookProperties, correlationHandlerMock, (e) -> {});

    // Register webhook function 'implementation'
    webhookConnectorRegistry.register(
        new ActiveInboundConnector(webhookConnectorExecutable, webhookContext));

    ResponseEntity<InboundConnectorResult<?>> responseEntity =
        (ResponseEntity<InboundConnectorResult<?>>)
            controller.inbound(
                "myPath",
                new HashMap<>(),
                "{}".getBytes(),
                new HashMap<>(),
                new MockHttpServletRequest());

    assertEquals(200, responseEntity.getStatusCode().value());
    assertFalse(Objects.requireNonNull(responseEntity.getBody()).isActivated());
  }

  @Test
  public void testSuccessfulProcessingWithErrorDuringActivation() throws Exception {
    WebhookConnectorExecutable webhookConnectorExecutable = mock(WebhookConnectorExecutable.class);
    when(webhookConnectorExecutable.triggerWebhook(any(WebhookProcessingPayload.class)))
        .thenReturn(mock(WebhookProcessingResult.class));

    var webhookProperties = webhookProperties("processA", 1, "myPath");
    var webhookContext =
        new InboundConnectorContextImpl(
            secretProvider, v -> {}, webhookProperties, correlationHandler, (e) -> {});

    // Register webhook function 'implementation'
    webhookConnectorRegistry.register(
        new ActiveInboundConnector(webhookConnectorExecutable, webhookContext));

    deployProcess("processB");

    ResponseEntity<?> responseEntity =
        controller.inbound(
            "myPath",
            new HashMap<>(),
            "{}".getBytes(),
            new HashMap<>(),
            new MockHttpServletRequest());
    assertEquals(500, responseEntity.getStatusCode().value());
  }

  @Test
  public void testErrorDuringProcessing() throws Exception {
    WebhookConnectorExecutable webhookConnectorExecutable = mock(WebhookConnectorExecutable.class);
    when(webhookConnectorExecutable.triggerWebhook(any(WebhookProcessingPayload.class)))
        .thenThrow(new RuntimeException("Error from webhook connector!"));

    var webhookProperties = webhookProperties("processA", 1, "myPath");
    var webhookContext =
        new InboundConnectorContextImpl(
            secretProvider, v -> {}, webhookProperties, correlationHandler, (e) -> {});

    // Register webhook function 'implementation'
    webhookConnectorRegistry.register(
        new ActiveInboundConnector(webhookConnectorExecutable, webhookContext));

    deployProcess("processA");

    ResponseEntity<?> responseEntity =
        controller.inbound(
            "myPath",
            new HashMap<>(),
            "{}".getBytes(),
            new HashMap<>(),
            new MockHttpServletRequest());

    assertEquals(500, responseEntity.getStatusCode().value());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testFeelExpressionErrorDuringProcessing() throws Exception {
    WebhookConnectorExecutable webhookConnectorExecutable = mock(WebhookConnectorExecutable.class);
    when(webhookConnectorExecutable.triggerWebhook(any(WebhookProcessingPayload.class)))
        .thenThrow(new FeelEngineWrapperException("reason", "expression", null));

    var webhookProperties = webhookProperties("processA", 1, "myPath");
    var webhookContext =
        new InboundConnectorContextImpl(
            secretProvider, v -> {}, webhookProperties, correlationHandler, (e) -> {});

    // Register webhook function 'implementation'
    webhookConnectorRegistry.register(
        new ActiveInboundConnector(webhookConnectorExecutable, webhookContext));

    deployProcess("processA");

    ResponseEntity<FeelExpressionErrorResponse> responseEntity =
        (ResponseEntity<FeelExpressionErrorResponse>)
            controller.inbound(
                "myPath",
                new HashMap<>(),
                "{}".getBytes(),
                new HashMap<>(),
                new MockHttpServletRequest());

    assertEquals(422, responseEntity.getStatusCode().value());
    assertEquals("reason", responseEntity.getBody().reason());
    assertEquals("expression", responseEntity.getBody().expression());
  }

  public void deployProcess(String bpmnProcessId) {
    zeebeClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(bpmnProcessId).startEvent().endEvent().done(),
            bpmnProcessId + ".bpmn")
        .send()
        .join();
  }
}
