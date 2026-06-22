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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.appintegrations.model.AppIntegrationsConfiguration;
import io.camunda.connector.appintegrations.model.CreateChannelRequest;
import io.camunda.connector.appintegrations.model.CreateChannelResult;
import io.camunda.connector.appintegrations.model.SendMessageRequest;
import io.camunda.connector.appintegrations.model.SendMessageResult;
import io.camunda.connector.appintegrations.model.auth.ApiKeyAuthentication;
import io.camunda.connector.appintegrations.model.auth.OAuthAuthentication;
import io.camunda.connector.http.client.authentication.OAuthConstants;
import io.camunda.connector.http.client.authentication.OAuthTokenCache;
import io.camunda.connector.http.client.authentication.OAuthTokenCacheHolder;
import io.camunda.connector.http.client.authentication.cacheimpl.CaffeineOAuthTokenCache;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.mapper.HttpResponse;
import io.camunda.connector.http.client.model.HttpClientRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppIntegrationsConnectorTest {

  private static final AppIntegrationsConfiguration CONFIG =
      new AppIntegrationsConfiguration(
          "https://app-integrations.example.com", new ApiKeyAuthentication("test-key"));

  private static final AppIntegrationsConfiguration CONFIG_OAUTH =
      new AppIntegrationsConfiguration(
          "https://app-integrations.example.com",
          new OAuthAuthentication(
              "https://auth.example.com/oauth/token",
              "client-id",
              "client-secret",
              "app-integrations",
              OAuthConstants.CREDENTIALS_BODY,
              null));

  private static final Validator VALIDATOR =
      Validation.byDefaultProvider()
          .configure()
          .messageInterpolator(new ParameterMessageInterpolator())
          .buildValidatorFactory()
          .getValidator();

  @Mock private HttpClient httpClient;
  @Mock private OutboundConnectorContext context;
  @Mock private JobContext jobContext;
  @Mock private OAuthTokenCache tokenCache;

  private AppIntegrationsConnector connector;

  @BeforeEach
  void setUp() {
    connector = new AppIntegrationsConnector(new ObjectMapper(), httpClient);
    when(context.getJobContext()).thenReturn(jobContext);
    when(jobContext.getCustomHeaders()).thenReturn(Map.of());
  }

  @AfterEach
  void tearDown() {
    // Restore a clean default cache so the static holder does not leak the mock across tests.
    OAuthTokenCacheHolder.set(new CaffeineOAuthTokenCache());
  }

  private static HttpResponse<String> httpResponse(int status, String body) {
    return new HttpResponse<>(status, "reason", Map.of(), body);
  }

  private HttpClientRequest captureRequest() {
    var captor = ArgumentCaptor.forClass(HttpClientRequest.class);
    verify(httpClient).execute(captor.capture(), any());
    return captor.getValue();
  }

  @Test
  void sendMessage_withOAuth_delegatesOAuthToHttpClient() {
    doReturn(httpResponse(201, "{\"conversation\":null}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = new SendMessageRequest(CONFIG_OAUTH, "user@example.com", null, "Hi", null);
    connector.sendMessage(request, context);

    // OAuth is delegated to the SDK HttpClient: the request carries the SDK OAuthAuthentication
    // (which execute() resolves into a Bearer token), not a hand-set Authorization header.
    var sent = captureRequest();
    assertThat(sent.getHeader("Authorization")).isEmpty();
    assertThat(sent.getAuthentication())
        .isInstanceOf(io.camunda.connector.http.client.model.auth.OAuthAuthentication.class);
    var oauth =
        (io.camunda.connector.http.client.model.auth.OAuthAuthentication) sent.getAuthentication();
    assertThat(oauth.clientId()).isEqualTo("client-id");
    assertThat(oauth.oauthTokenEndpoint()).isEqualTo("https://auth.example.com/oauth/token");
    assertThat(oauth.audience()).isEqualTo("app-integrations");
  }

  @Test
  void sendMessage_oauth401_invalidatesTokenAndRetries() {
    OAuthTokenCacheHolder.set(tokenCache);
    doReturn(httpResponse(401, "Unauthorized"), httpResponse(201, "{\"conversation\":null}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = new SendMessageRequest(CONFIG_OAUTH, "user@example.com", null, "Hi", null);
    var result = connector.sendMessage(request, context);

    assertThat(result.conversation()).isNull();
    verify(tokenCache)
        .invalidate(any(io.camunda.connector.http.client.model.auth.OAuthAuthentication.class));
    verify(httpClient, times(2)).execute(any(HttpClientRequest.class), any());
  }

  @Test
  void sendMessage_notSaas_omitsContextHeaders() {
    doReturn(httpResponse(201, "{\"conversation\":null}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = new SendMessageRequest(CONFIG, "user@example.com", null, "Hi", null);
    connector.sendMessage(request, context);

    var req = captureRequest();
    assertThat(req.getHeader("X-Org-Id")).isEmpty();
    assertThat(req.getHeader("X-Cluster-Id")).isEmpty();
  }

  @Test
  void sendMessage_byEmail_callsCorrectEndpointWithApiKeyHeader() {
    doReturn(httpResponse(201, "{\"conversation\":null}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request =
        new SendMessageRequest(CONFIG, "user@example.com", null, "Hello from Camunda", null);
    var result = connector.sendMessage(request, context);

    assertThat(result).isInstanceOf(SendMessageResult.class);
    assertThat(result.conversation()).isNull();

    var req = captureRequest();
    assertThat(req.getUrl()).endsWith("/api/connector/message");
    assertThat(req.getHeader("X-API-KEY")).hasValue("test-key");
  }

  @Test
  void sendMessage_byChannelId_sendsChannelIdInBodyAndOmitsEmail() {
    doReturn(httpResponse(201, "{\"conversation\":null}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request =
        new SendMessageRequest(CONFIG, null, "19:abc123@thread.tacv2", "Hello from Camunda", null);
    var result = connector.sendMessage(request, context);

    assertThat(result.conversation()).isNull();

    var body = (String) captureRequest().getBody();
    assertThat(body).contains("\"channelId\":\"19:abc123@thread.tacv2\"");
    assertThat(body).contains("\"message\":\"Hello from Camunda\"");
    assertThat(body).doesNotContain("\"email\"");
  }

  @Test
  void sendMessage_backendError_throwsConnectorException() {
    doReturn(httpResponse(500, "Internal Server Error"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = new SendMessageRequest(CONFIG, "user@example.com", null, "Hello", null);

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("500");
  }

  @Test
  void sendMessage_transportError_throwsConnectorException() {
    doThrow(new ConnectorException("IO_ERROR", "Connection refused"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = new SendMessageRequest(CONFIG, "user@example.com", null, "Hello", null);

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Connection refused");
  }

  @Test
  void sendMessageRequest_neitherEmailNorChannelId_failsValidation() {
    var request = new SendMessageRequest(CONFIG, null, null, "Hello", null);
    var violations = VALIDATOR.validate(request);
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getMessage().contains("Exactly one of 'email' or 'channelId'"));
  }

  @Test
  void sendMessageRequest_bothEmailAndChannelId_failsValidation() {
    var request =
        new SendMessageRequest(CONFIG, "user@example.com", "19:abc@thread.tacv2", "Hello", null);
    var violations = VALIDATOR.validate(request);
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getMessage().contains("Exactly one of 'email' or 'channelId'"));
  }

  @Test
  void sendMessageRequest_bothMessageAndAdaptiveCard_failsValidation() {
    var request =
        new SendMessageRequest(
            CONFIG, "user@example.com", null, "Hello", "{\"type\":\"AdaptiveCard\"}");
    var violations = VALIDATOR.validate(request);
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(
            v ->
                v.getMessage()
                    .contains("'message' and 'adaptiveCardJson' cannot both be provided"));
  }

  @Test
  void sendMessage_withLinkedFormResource_includesFormResourceKey() {
    doReturn(httpResponse(201, "{\"conversation\":null}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());
    when(jobContext.getCustomHeaders())
        .thenReturn(
            Map.of(
                "linkedResources",
                "[{\"resourceKey\":\"12345\",\"resourceType\":\"form\",\"linkName\":\"approval\"}]"));

    var request = new SendMessageRequest(CONFIG, "user@example.com", null, "Please approve", null);
    connector.sendMessage(request, context);

    var body = (String) captureRequest().getBody();
    assertThat(body).contains("\"formResourceKey\":\"12345\"");
    assertThat(body).contains("\"message\":\"Please approve\"");
  }

  @Test
  void sendMessage_noLinkedResources_omitsFormFieldsFromBody() {
    doReturn(httpResponse(201, "{\"conversation\":null}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = new SendMessageRequest(CONFIG, "user@example.com", null, "Hello", null);
    connector.sendMessage(request, context);

    var body = (String) captureRequest().getBody();
    assertThat(body).doesNotContain("formResourceKey");
  }

  @Test
  void sendMessage_malformedLinkedResourcesHeader_sendsMessageWithoutForm() {
    doReturn(httpResponse(201, "{\"conversation\":null}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());
    when(jobContext.getCustomHeaders()).thenReturn(Map.of("linkedResources", "not-valid-json"));

    var request = new SendMessageRequest(CONFIG, "user@example.com", null, "Hello", null);
    var result = connector.sendMessage(request, context);

    assertThat(result.conversation()).isNull();
    assertThat((String) captureRequest().getBody()).doesNotContain("formResourceKey");
  }

  @Test
  void sendMessage_noContentProvided_throwsValidationError() {
    var request = new SendMessageRequest(CONFIG, "user@example.com", null, null, null);

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining(
            "One of 'message', 'adaptiveCardJson', or a linked form must be provided");
  }

  // --- createChannel ---

  @Test
  void createChannel_success_returnsChannelIdAndVerifiesRequestBody() {
    doReturn(httpResponse(201, "{\"channelId\":\"19:new-channel@thread.tacv2\"}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request =
        new CreateChannelRequest(
            CONFIG, "b7779302-e8cb-4b34-901b-5b150a19fd47", "My Channel", null, "standard");
    var result = connector.createChannel(request);

    assertThat(result).isInstanceOf(CreateChannelResult.class);
    assertThat(result.channelId()).isEqualTo("19:new-channel@thread.tacv2");

    var req = captureRequest();
    var body = (String) req.getBody();
    assertThat(body).contains("\"teamId\":\"b7779302-e8cb-4b34-901b-5b150a19fd47\"");
    assertThat(body).contains("\"displayName\":\"My Channel\"");
    assertThat(body).contains("\"membershipType\":\"standard\"");
    assertThat(body).doesNotContain("\"description\"");
    assertThat(req.getUrl()).endsWith("/api/connector/channel");
    assertThat(req.getHeader("X-API-KEY")).hasValue("test-key");
  }

  @Test
  void createChannel_teamsUrl_extractsGroupIdBeforeSending() {
    doReturn(httpResponse(201, "{\"channelId\":\"19:new@thread.tacv2\"}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request =
        new CreateChannelRequest(
            CONFIG,
            "https://teams.cloud.microsoft/l/team/19%3Axxx?groupId=b7779302-e8cb-4b34-901b-5b150a19fd47&tenantId=abc",
            "My Channel",
            null,
            "standard");
    connector.createChannel(request);

    assertThat((String) captureRequest().getBody())
        .contains("\"teamId\":\"b7779302-e8cb-4b34-901b-5b150a19fd47\"");
  }

  @Test
  void createChannel_backendError_throwsConnectorException() {
    doReturn(httpResponse(500, "Internal Server Error"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request =
        new CreateChannelRequest(
            CONFIG, "b7779302-e8cb-4b34-901b-5b150a19fd47", "My Channel", null, "standard");
    assertThatThrownBy(() -> connector.createChannel(request))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("500");
  }

  @Test
  void createChannel_transportError_throwsConnectorException() {
    doThrow(new ConnectorException("IO_ERROR", "Connection refused"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request =
        new CreateChannelRequest(
            CONFIG, "b7779302-e8cb-4b34-901b-5b150a19fd47", "My Channel", null, "standard");
    assertThatThrownBy(() -> connector.createChannel(request))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Connection refused");
  }
}
