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
import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.grpc.Status;
import jakarta.servlet.MultipartConfigElement;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletRegistrationBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.DispatcherServlet;

@SpringBootTest(
    classes = {
      TestConnectorRuntimeApplication.class,
      WebhookCustomDispatcherServletMappingTest.CustomDispatcherServletPathConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=true",
      "camunda.connector.polling.enabled=false",
    })
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookCustomDispatcherServletMappingTest {

  @MockitoBean private ZeebeClient zeebeClient;

  @MockitoBean private OutboundConnectorFactory outboundConnectorFactory;

  @Autowired private WebhookConnectorRegistry webhookConnectorRegistry;

  @Autowired private SecretProviderAggregator secretProvider;

  @Autowired private ObjectMapper mapper;

  @MockitoBean private InboundCorrelationHandler correlationHandler;

  @LocalServerPort private int port;

  private ArgumentCaptor<WebhookProcessingPayload> payloadCaptor;

  @BeforeEach
  void beforeEach() throws Exception {
    webhookConnectorRegistry.reset();
    when(zeebeClient.newCreateInstanceCommand())
        .thenThrow(new ClientStatusException(Status.INVALID_ARGUMENT, new Exception()));
    when(correlationHandler.correlate(anyList(), any()))
        .thenReturn(
            new CorrelationResult.Failure.ZeebeClientStatus(
                Status.Code.INVALID_ARGUMENT.name(), "invalid input"));

    var executable = mock(WebhookConnectorExecutable.class);
    var webhookResult = mock(WebhookResult.class);
    when(webhookResult.request()).thenReturn(new MappedHttpRequest(Map.of(), Map.of(), Map.of()));
    payloadCaptor = ArgumentCaptor.forClass(WebhookProcessingPayload.class);
    when(executable.triggerWebhook(payloadCaptor.capture())).thenReturn(webhookResult);

    var details = webhookDefinition("processA", 1, "formPath");
    var context =
        new InboundConnectorContextImpl(
            secretProvider,
            new DefaultValidationProvider(),
            details,
            correlationHandler,
            e -> {},
            mapper,
            EvictingQueue.create(10));
    webhookConnectorRegistry.register(new RegisteredExecutable.Activated(executable, context));
  }

  @Configuration
  static class CustomDispatcherServletPathConfig {
    @Bean(name = "dispatcherServletRegistration")
    DispatcherServletRegistrationBean dispatcherServletRegistration(
        DispatcherServlet dispatcherServlet) {
      var registration = new DispatcherServletRegistrationBean(dispatcherServlet, "/api");
      registration.setLoadOnStartup(1);
      registration.setMultipartConfig(new MultipartConfigElement(""));
      return registration;
    }
  }

  @Test
  void assertsSetup() throws Exception {
    var response = sendPut("/inbound/formPath", "field=12345678");

    assertThat(response.statusCode()).isEqualTo(404);
  }

  @Test
  void formUrlencodedBodyIsFullyBufferedUnderTheUnannouncedCustomMapping() throws Exception {
    var sentBody = "field=" + "x".repeat(64);

    var response = sendPut("/api/inbound/formPath", sentBody);

    assertThat(response.statusCode()).isEqualTo(400);
    var receivedBody = new String(payloadCaptor.getValue().rawBody(), StandardCharsets.UTF_8);
    assertThat(receivedBody).isEqualTo(sentBody);
  }

  @ParameterizedTest
  @ValueSource(strings = {"multipart/related; boundary=x", "multipart/mixed; boundary=x"})
  void nonFormMultipartBodyIsPreservedUnderTheUnannouncedCustomMapping(String contentType)
      throws Exception {
    var sentBody =
        """
        --x\r
        Content-Type: application/json\r
        \r
        {"message":"payload"}\r
        --x--\r
        """;

    var response = sendPut("/api/inbound/formPath", sentBody, contentType);

    assertThat(response.statusCode()).isEqualTo(400);
    var receivedBody = new String(payloadCaptor.getValue().rawBody(), StandardCharsets.UTF_8);
    assertThat(receivedBody).isEqualTo(sentBody);
  }

  private HttpResponse<String> sendPut(String path, String body) throws Exception {
    return sendPut(path, body, "application/x-www-form-urlencoded");
  }

  private HttpResponse<String> sendPut(String path, String body, String contentType)
      throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", contentType)
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }
}
