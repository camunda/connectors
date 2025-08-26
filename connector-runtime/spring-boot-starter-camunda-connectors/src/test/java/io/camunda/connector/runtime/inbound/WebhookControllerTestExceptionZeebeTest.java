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

import static io.camunda.connector.runtime.inbound.BaseWebhookTest.webhookDefinition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import io.camunda.connector.runtime.inbound.webhook.InboundWebhookRestController;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.grpc.Status;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=true",
    })
@CamundaSpringProcessTest
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookControllerTestExceptionZeebeTest {

  @Autowired private WebhookConnectorRegistry webhookConnectorRegistry;

  @MockitoBean private CamundaClient camundaClient;

  @Autowired private SecretProviderAggregator secretProvider;

  @Autowired private ObjectMapper mapper;

  @Autowired private InboundCorrelationHandler correlationHandler;

  @Autowired @InjectMocks private InboundWebhookRestController controller;

  @BeforeEach
  public void beforeEach() {
    System.out.println("System property 'quickly': " + System.getProperty("quickly"));
    webhookConnectorRegistry.reset();
  }

  @Test
  public void test429IsReturnedWhenResourcesExhausted() throws Exception {

    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.RESOURCE_EXHAUSTED, new Exception()));

    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(429, responseEntity.getStatusCode().value());
  }

  @Test
  public void test499IsReturnedWhenCancelled() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.CANCELLED, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();
    assertEquals(499, responseEntity.getStatusCode().value());
  }

  @Test
  public void test500IsReturnedWhenUnknown() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.UNKNOWN, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(500, responseEntity.getStatusCode().value());
  }

  @Test
  public void test400IsReturnedWhenInvalidArgument() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.INVALID_ARGUMENT, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(400, responseEntity.getStatusCode().value());
  }

  @Test
  public void test504IsReturnedWhenDeadlineExceeded() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.DEADLINE_EXCEEDED, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(504, responseEntity.getStatusCode().value());
  }

  @Test
  public void test404IsReturnedWhenNotFound() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.NOT_FOUND, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(404, responseEntity.getStatusCode().value());
  }

  @Test
  public void test409IsReturnedWhenAlreadyExists() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.ALREADY_EXISTS, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(409, responseEntity.getStatusCode().value());
  }

  @Test
  public void test403IsReturnedWhenPermissionDenied() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.PERMISSION_DENIED, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(403, responseEntity.getStatusCode().value());
  }

  @Test
  public void test412IsReturnedWhenFailedPrecondition() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.FAILED_PRECONDITION, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(412, responseEntity.getStatusCode().value());
  }

  @Test
  public void test409IsReturnedWhenAborted() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.ABORTED, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(409, responseEntity.getStatusCode().value());
  }

  @Test
  public void test416IsReturnedWhenOutOfRange() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.OUT_OF_RANGE, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(416, responseEntity.getStatusCode().value());
  }

  @Test
  public void test501IsReturnedWhenUnimplemented() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.UNIMPLEMENTED, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(501, responseEntity.getStatusCode().value());
  }

  @Test
  public void test500IsReturnedWhenInternal() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.INTERNAL, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(500, responseEntity.getStatusCode().value());
  }

  @Test
  public void test503IsReturnedWhenUnavailable() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.UNAVAILABLE, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(503, responseEntity.getStatusCode().value());
  }

  @Test
  public void test500IsReturnedWhenDataLoss() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.DATA_LOSS, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(500, responseEntity.getStatusCode().value());
  }

  @Test
  public void test401IsReturnedWhenUnauthenticated() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.UNAUTHENTICATED, new Exception()));
    ResponseEntity<?> responseEntity = getResponseEntity();

    assertEquals(401, responseEntity.getStatusCode().value());
  }

  private ResponseEntity<?> getResponseEntity() throws Exception {
    WebhookConnectorExecutable webhookConnectorExecutable = mock(WebhookConnectorExecutable.class);
    WebhookResult webhookResult = mock(WebhookResult.class);
    when(webhookResult.request()).thenReturn(new MappedHttpRequest(Map.of(), Map.of(), Map.of()));
    when(webhookConnectorExecutable.triggerWebhook(any(WebhookProcessingPayload.class)))
        .thenReturn(webhookResult);

    var webhookDef = webhookDefinition("processA", 1, "myPath");
    var webhookContext =
        new InboundConnectorContextImpl(
            secretProvider,
            v -> {},
            webhookDef,
            correlationHandler,
            (e) -> {},
            mapper,
            new ActivityLogRegistry());

    webhookConnectorRegistry.register(
        new RegisteredExecutable.Activated(
            webhookConnectorExecutable,
            webhookContext,
            ExecutableId.fromDeduplicationId("random")));

    return controller.inbound(
        "myPath", new HashMap<>(), "{}".getBytes(), new HashMap<>(), new MockHttpServletRequest());
  }
}
