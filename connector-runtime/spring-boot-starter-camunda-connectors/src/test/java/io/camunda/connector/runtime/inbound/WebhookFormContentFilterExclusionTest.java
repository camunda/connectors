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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import io.grpc.Status;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=true",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.webhook.max-request-body-bytes=8",
    })
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookFormContentFilterExclusionTest {

  @MockitoBean private CamundaClient camundaClient;

  @MockitoBean private OutboundConnectorFactory outboundConnectorFactory;

  @Autowired private WebhookConnectorRegistry webhookConnectorRegistry;

  @Autowired private SecretProviderAggregator secretProvider;

  @Autowired private ObjectMapper mapper;

  @MockitoBean private InboundCorrelationHandler correlationHandler;

  @LocalServerPort private int port;

  @BeforeEach
  void beforeEach() {
    webhookConnectorRegistry.reset();
    when(correlationHandler.correlate(anyList(), any()))
        .thenReturn(
            new CorrelationResult.Failure.ZeebeClientStatus(
                Status.Code.INVALID_ARGUMENT.name(), "invalid input"));
  }

  @Test
  void shouldReturn404ForOversizedFormUrlEncodedPutToUnregisteredPath() throws Exception {
    var response = sendFormRequest("PUT", "/inbound/doesNotExist", "field=" + "x".repeat(64));
    assertThat(response.statusCode()).isEqualTo(404);
  }

  @Test
  void shouldReturn404ForOversizedFormUrlEncodedDeleteToUnregisteredPath() throws Exception {
    var response = sendFormRequest("DELETE", "/inbound/doesNotExist", "field=" + "x".repeat(64));
    assertThat(response.statusCode()).isEqualTo(404);
  }

  @Test
  void shouldStillProcessWithinLimitFormUrlEncodedPutToRegisteredPath() throws Exception {
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.INVALID_ARGUMENT, new Exception()));

    var executable = mock(WebhookConnectorExecutable.class);
    var webhookResult = mock(WebhookResult.class);
    when(webhookResult.request()).thenReturn(new MappedHttpRequest(Map.of(), Map.of(), Map.of()));
    when(executable.triggerWebhook(any(WebhookProcessingPayload.class))).thenReturn(webhookResult);

    var details = webhookDefinition("processA", 1, "formPath");
    var context =
        new InboundConnectorContextImpl(
            secretProvider,
            new DefaultValidationProvider(),
            details,
            correlationHandler,
            e -> {},
            mapper,
            new ActivityLogRegistry());
    webhookConnectorRegistry.register(
        new RegisteredExecutable.Activated(
            executable, context, ExecutableId.fromDeduplicationId(details.deduplicationId())));

    var response = sendFormRequest("PUT", "/inbound/formPath", "a=1");

    assertThat(response.statusCode()).isEqualTo(400);
  }

  private HttpResponse<String> sendFormRequest(String method, String path, String body)
      throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .method(method, HttpRequest.BodyPublishers.ofString(body))
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }
}
