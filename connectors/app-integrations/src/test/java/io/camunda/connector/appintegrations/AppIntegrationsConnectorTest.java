/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.appintegrations.model.AppIntegrationsConfiguration;
import io.camunda.connector.appintegrations.model.SendMessageRequest;
import io.camunda.connector.appintegrations.model.SendMessageResult;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppIntegrationsConnectorTest {

  private static final AppIntegrationsConfiguration CONFIG =
      new AppIntegrationsConfiguration("https://app-integrations.example.com", "test-token");

  private static final Validator VALIDATOR =
      Validation.byDefaultProvider()
          .configure()
          .messageInterpolator(new ParameterMessageInterpolator())
          .buildValidatorFactory()
          .getValidator();

  @Mock private HttpClient httpClient;
  @Mock private HttpResponse<String> httpResponse;
  @Mock private OutboundConnectorContext context;

  private AppIntegrationsConnector connector;

  @BeforeEach
  void setUp() {
    connector = new AppIntegrationsConnector(new ObjectMapper(), httpClient);
  }

  @Test
  void sendMessage_byEmail_callsCorrectEndpointWithEmailInBody()
      throws IOException, InterruptedException {
    when(httpResponse.statusCode()).thenReturn(201);
    when(httpResponse.body()).thenReturn("{\"conversation\":null}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    var request = new SendMessageRequest(CONFIG, "user@example.com", null, "Hello from Camunda");
    var result = connector.sendMessage(request, context);

    assertThat(result).isInstanceOf(SendMessageResult.class);
    assertThat(result.conversation()).isNull();

    verify(httpClient)
        .send(
            argThat(
                req -> {
                  var uri = req.uri().toString();
                  var auth = req.headers().firstValue("Authorization").orElse("");
                  return uri.endsWith("/api/connector/message") && auth.equals("Bearer test-token");
                }),
            any());
  }

  @Test
  void sendMessage_byChannelId_sendsChannelIdInBodyAndOmitsEmail()
      throws IOException, InterruptedException {
    when(httpResponse.statusCode()).thenReturn(201);
    when(httpResponse.body()).thenReturn("{\"conversation\":null}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    var request =
        new SendMessageRequest(CONFIG, null, "19:abc123@thread.tacv2", "Hello from Camunda");
    var result = connector.sendMessage(request, context);

    assertThat(result.conversation()).isNull();

    var captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any());
    var body = readBody(captor.getValue());
    assertThat(body).contains("\"channelId\":\"19:abc123@thread.tacv2\"");
    assertThat(body).contains("\"message\":\"Hello from Camunda\"");
    assertThat(body).doesNotContain("\"email\"");
  }

  private static String readBody(HttpRequest request) {
    var publisher = request.bodyPublisher().orElseThrow();
    var baos = new ByteArrayOutputStream();
    publisher.subscribe(
        new Flow.Subscriber<>() {
          @Override
          public void onSubscribe(Flow.Subscription s) {
            s.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(ByteBuffer item) {
            var b = new byte[item.remaining()];
            item.get(b);
            baos.writeBytes(b);
          }

          @Override
          public void onError(Throwable t) {}

          @Override
          public void onComplete() {}
        });
    return baos.toString(StandardCharsets.UTF_8);
  }

  @Test
  void sendMessage_backendError_throwsConnectorException()
      throws IOException, InterruptedException {
    when(httpResponse.statusCode()).thenReturn(500);
    when(httpResponse.body()).thenReturn("Internal Server Error");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    var request = new SendMessageRequest(CONFIG, "user@example.com", null, "Hello");

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("500");
  }

  @Test
  void sendMessage_ioException_throwsConnectorException() throws IOException, InterruptedException {
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("Connection refused"));

    var request = new SendMessageRequest(CONFIG, "user@example.com", null, "Hello");

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Connection refused");
  }

  @Test
  void sendMessage_interrupted_setsInterruptFlagAndThrows()
      throws IOException, InterruptedException {
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new InterruptedException("interrupted"));

    var request = new SendMessageRequest(CONFIG, "user@example.com", null, "Hello");

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("interrupted");

    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    Thread.interrupted(); // clear flag for test runner
  }

  @Test
  void sendMessageRequest_neitherEmailNorChannelId_failsValidation() {
    var request = new SendMessageRequest(CONFIG, null, null, "Hello");
    var violations = VALIDATOR.validate(request);
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getMessage().contains("Exactly one of 'email' or 'channelId'"));
  }

  @Test
  void sendMessageRequest_bothEmailAndChannelId_failsValidation() {
    var request =
        new SendMessageRequest(CONFIG, "user@example.com", "19:abc@thread.tacv2", "Hello");
    var violations = VALIDATOR.validate(request);
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getMessage().contains("Exactly one of 'email' or 'channelId'"));
  }
}
