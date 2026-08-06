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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.appintegrations.model.CreateChannelRequest;
import io.camunda.connector.appintegrations.model.CreateChannelResult;
import io.camunda.connector.appintegrations.model.MessageContent;
import io.camunda.connector.appintegrations.model.Recipient;
import io.camunda.connector.appintegrations.model.SendMessageRequest;
import io.camunda.connector.appintegrations.model.SendMessageResult;
import io.camunda.connector.http.client.authentication.OAuthTokenCache;
import io.camunda.connector.http.client.authentication.OAuthTokenCacheHolder;
import io.camunda.connector.http.client.authentication.cacheimpl.CaffeineOAuthTokenCache;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.mapper.HttpResponse;
import io.camunda.connector.http.client.model.HttpClientRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

  private static final String BASE_URL = "https://app-integrations.example.com";
  private static final String TOKEN_ENDPOINT = "https://auth.example.com/oauth/token";

  private static final Map<String, String> API_KEY_ENV =
      Map.of("APP_INTEGRATIONS_BASE_URL", BASE_URL, "APP_INTEGRATIONS_API_KEY", "test-key");

  private static final Map<String, String> OAUTH_ENV =
      Map.of(
          "APP_INTEGRATIONS_BASE_URL", BASE_URL,
          "APP_INTEGRATIONS_OAUTH_TOKEN_ENDPOINT", TOKEN_ENDPOINT,
          "APP_INTEGRATIONS_OAUTH_CLIENT_ID", "client-id",
          "APP_INTEGRATIONS_OAUTH_CLIENT_SECRET", "client-secret",
          "APP_INTEGRATIONS_OAUTH_AUDIENCE", "app-integrations");

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
    connector = connectorWith(API_KEY_ENV);
    when(context.getJobContext()).thenReturn(jobContext);
    when(jobContext.getCustomHeaders()).thenReturn(Map.of());
  }

  @AfterEach
  void tearDown() {
    // Restore a clean default cache so the static holder does not leak the mock across tests.
    OAuthTokenCacheHolder.set(new CaffeineOAuthTokenCache());
  }

  private AppIntegrationsConnector connectorWith(Map<String, String> env) {
    return new AppIntegrationsConnector(new ObjectMapper(), httpClient, env::get);
  }

  private static HttpResponse<String> httpResponse(int status, String body) {
    return new HttpResponse<>(status, "reason", Map.of(), body);
  }

  private void stubOk() {
    doReturn(httpResponse(201, "{\"conversation\":null}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());
  }

  private HttpClientRequest captureRequest() {
    var captor = ArgumentCaptor.forClass(HttpClientRequest.class);
    verify(httpClient).execute(captor.capture(), any());
    return captor.getValue();
  }

  private static SendMessageRequest textToEmail(String email, String message) {
    return new SendMessageRequest(
        new Recipient.CamundaRecipient(email, null, null), new MessageContent.TextContent(message));
  }

  private static CreateChannelRequest channelRequest(String teamId) {
    return new CreateChannelRequest(teamId, "My Channel", null, "standard");
  }

  // --- authentication and transport ---

  @Test
  void sendMessage_withOAuth_delegatesOAuthToHttpClient() {
    stubOk();

    connectorWith(OAUTH_ENV).sendMessage(textToEmail("user@example.com", "Hi"), context);

    // OAuth is delegated to the SDK HttpClient: the request carries the SDK OAuthAuthentication
    // (which execute() resolves into a Bearer token), not a hand-set Authorization header.
    var sent = captureRequest();
    assertThat(sent.getHeader("Authorization")).isEmpty();
    assertThat(sent.getHeader("X-API-KEY")).isEmpty();
    assertThat(sent.getAuthentication())
        .isInstanceOf(io.camunda.connector.http.client.model.auth.OAuthAuthentication.class);
    var oauth =
        (io.camunda.connector.http.client.model.auth.OAuthAuthentication) sent.getAuthentication();
    assertThat(oauth.clientId()).isEqualTo("client-id");
    assertThat(oauth.oauthTokenEndpoint()).isEqualTo(TOKEN_ENDPOINT);
    assertThat(oauth.audience()).isEqualTo("app-integrations");
  }

  @Test
  void oauthAndApiKeyBothConfigured_oauthWins() {
    stubOk();
    var env = new java.util.HashMap<>(OAUTH_ENV);
    env.put("APP_INTEGRATIONS_API_KEY", "unused-key");

    connectorWith(env).sendMessage(textToEmail("user@example.com", "Hi"), context);

    var sent = captureRequest();
    assertThat(sent.getAuthentication())
        .isInstanceOf(io.camunda.connector.http.client.model.auth.OAuthAuthentication.class);
    assertThat(sent.getHeader("X-API-KEY")).isEmpty();
  }

  @Test
  void sendMessage_oauth401_invalidatesTokenAndRetries() {
    OAuthTokenCacheHolder.set(tokenCache);
    // The SDK HttpClient throws (errorCode = status code) on a 401, it does not return it. The
    // connector must catch that, invalidate the cached token, and retry once — the retry succeeds.
    doThrow(new ConnectorException("401", "Unauthorized"))
        .doReturn(httpResponse(201, "{\"conversation\":null}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var result =
        connectorWith(OAUTH_ENV).sendMessage(textToEmail("user@example.com", "Hi"), context);

    assertThat(result.conversation()).isNull();
    verify(tokenCache)
        .invalidate(any(io.camunda.connector.http.client.model.auth.OAuthAuthentication.class));
    verify(httpClient, times(2)).execute(any(HttpClientRequest.class), any());
  }

  @Test
  void sendMessage_apiKey401_propagatesWithoutRetry() {
    // A 401 with a non-OAuth auth has no cached token to refresh, so it must propagate as-is.
    doThrow(new ConnectorException("401", "Unauthorized"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = textToEmail("user@example.com", "Hi");

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOfSatisfying(
            ConnectorException.class, e -> assertThat(e.getErrorCode()).isEqualTo("401"));
    verify(httpClient, times(1)).execute(any(HttpClientRequest.class), any());
  }

  @Test
  void sendMessage_notSaas_omitsContextHeaders() {
    stubOk();

    connector.sendMessage(textToEmail("user@example.com", "Hi"), context);

    var req = captureRequest();
    assertThat(req.getHeader("X-Org-Id")).isEmpty();
    assertThat(req.getHeader("X-Cluster-Id")).isEmpty();
  }

  @Test
  void sendMessage_backendError_throwsConnectorException() {
    // The SDK HttpClient throws on status >= 400 with the status code as the error code.
    doThrow(new ConnectorException("500", "Internal Server Error"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = textToEmail("user@example.com", "Hello");

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOfSatisfying(
            ConnectorException.class, e -> assertThat(e.getErrorCode()).isEqualTo("500"));
  }

  @Test
  void sendMessage_transportError_throwsConnectorException() {
    doThrow(new ConnectorException("IO_ERROR", "Connection refused"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = textToEmail("user@example.com", "Hello");

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Connection refused");
  }

  // --- recipient ---

  @Test
  void sendMessage_byEmail_callsCorrectEndpointWithApiKeyHeader() {
    stubOk();

    var result =
        connector.sendMessage(textToEmail("user@example.com", "Hello from Camunda"), context);

    assertThat(result).isInstanceOf(SendMessageResult.class);
    assertThat(result.conversation()).isNull();

    var req = captureRequest();
    assertThat(req.getUrl()).endsWith("/api/connector/message");
    assertThat(req.getHeader("X-API-KEY")).hasValue("test-key");
  }

  @Test
  void sendMessage_byChannelId_sendsChannelIdInBodyAndOmitsEmail() {
    stubOk();

    var request =
        new SendMessageRequest(
            new Recipient.TeamsRecipient("19:abc123@thread.tacv2"),
            new MessageContent.TextContent("Hello from Camunda"));
    var result = connector.sendMessage(request, context);

    assertThat(result.conversation()).isNull();

    var body = (String) captureRequest().getBody();
    assertThat(body).contains("\"channelId\":\"19:abc123@thread.tacv2\"");
    assertThat(body).contains("\"message\":\"Hello from Camunda\"");
    assertThat(body).doesNotContain("\"email\"");
  }

  @Test
  void sendMessage_candidateUsersAndGroups_sentAsJsonArrays() {
    stubOk();

    var request =
        new SendMessageRequest(
            new Recipient.CamundaRecipient(null, List.of("alice", "bob"), List.of("approvers")),
            new MessageContent.TextContent("Please approve"));
    connector.sendMessage(request, context);

    var body = (String) captureRequest().getBody();
    assertThat(body).contains("\"candidateUsers\":[\"alice\",\"bob\"]");
    assertThat(body).contains("\"candidateGroups\":[\"approvers\"]");
    assertThat(body).doesNotContain("\"email\"");
    assertThat(body).doesNotContain("\"channelId\"");
  }

  @Test
  void sendMessage_emptyCandidateLists_omittedFromBody() {
    stubOk();

    var request =
        new SendMessageRequest(
            new Recipient.CamundaRecipient("user@example.com", List.of(), List.of()),
            new MessageContent.TextContent("Hello"));
    connector.sendMessage(request, context);

    var body = (String) captureRequest().getBody();
    assertThat(body).contains("\"email\":\"user@example.com\"");
    assertThat(body).doesNotContain("candidateUsers");
    assertThat(body).doesNotContain("candidateGroups");
  }

  @Test
  void camundaRecipient_withNothingProvided_failsValidation() {
    var request =
        new SendMessageRequest(
            new Recipient.CamundaRecipient(null, null, null),
            new MessageContent.TextContent("Hello"));

    var violations = VALIDATOR.validate(request);

    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(
            v ->
                v.getMessage()
                    .contains(
                        "At least one of 'email', 'candidateUsers' or 'candidateGroups' must be provided"));
  }

  @Test
  void camundaRecipient_withBlankEmailAndEmptyLists_failsValidation() {
    var request =
        new SendMessageRequest(
            new Recipient.CamundaRecipient("  ", List.of(), List.of()),
            new MessageContent.TextContent("Hello"));

    assertThat(VALIDATOR.validate(request))
        .anyMatch(v -> v.getMessage().contains("At least one of 'email'"));
  }

  @Test
  void teamsRecipient_withBlankChannelId_failsValidation() {
    var request =
        new SendMessageRequest(
            new Recipient.TeamsRecipient(""), new MessageContent.TextContent("Hello"));

    assertThat(VALIDATOR.validate(request)).isNotEmpty();
  }

  // --- message content ---

  @Test
  void sendMessage_adaptiveCard_sendsCardAndOmitsMessage() {
    stubOk();

    var request =
        new SendMessageRequest(
            new Recipient.CamundaRecipient("user@example.com", null, null),
            new MessageContent.AdaptiveCardContent("{\"type\":\"AdaptiveCard\"}"));
    connector.sendMessage(request, context);

    var body = (String) captureRequest().getBody();
    assertThat(body).contains("\"adaptiveCardJson\"");
    assertThat(body).doesNotContain("\"message\"");
  }

  @Test
  void textContent_withBlankMessage_failsValidation() {
    var request =
        new SendMessageRequest(
            new Recipient.CamundaRecipient("user@example.com", null, null),
            new MessageContent.TextContent(""));

    assertThat(VALIDATOR.validate(request)).isNotEmpty();
  }

  @Test
  void adaptiveCardContent_withBlankJson_failsValidation() {
    var request =
        new SendMessageRequest(
            new Recipient.CamundaRecipient("user@example.com", null, null),
            new MessageContent.AdaptiveCardContent(""));

    assertThat(VALIDATOR.validate(request)).isNotEmpty();
  }

  // --- linked form ---

  private static SendMessageRequest formToEmail() {
    return new SendMessageRequest(
        new Recipient.CamundaRecipient("user@example.com", null, null),
        new MessageContent.FormContent());
  }

  @Test
  void sendMessage_formContent_withLinkedFormResource_includesFormResourceKey() {
    stubOk();
    when(jobContext.getCustomHeaders())
        .thenReturn(
            Map.of(
                "linkedResources",
                "[{\"resourceKey\":\"12345\",\"resourceType\":\"form\",\"linkName\":\"formDefinition\"}]"));

    connector.sendMessage(formToEmail(), context);

    var body = (String) captureRequest().getBody();
    assertThat(body).contains("\"formResourceKey\":\"12345\"");
    assertThat(body).doesNotContain("\"message\"");
    assertThat(body).doesNotContain("\"adaptiveCardJson\"");
  }

  @Test
  void sendMessage_formContent_withoutLinkedForm_throwsValidationError() {
    var request = formToEmail();

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Message type is 'form' but no linked form was found on the job");
    verifyNoInteractions(httpClient);
  }

  @Test
  void sendMessage_formContent_malformedLinkedResourcesHeader_throwsValidationError() {
    when(jobContext.getCustomHeaders()).thenReturn(Map.of("linkedResources", "not-valid-json"));
    var request = formToEmail();

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("no linked form was found");
    verifyNoInteractions(httpClient);
  }

  @Test
  void sendMessage_textContent_ignoresLinkedResourcesHeader() {
    stubOk();
    when(jobContext.getCustomHeaders())
        .thenReturn(
            Map.of(
                "linkedResources",
                "[{\"resourceKey\":\"12345\",\"resourceType\":\"form\",\"linkName\":\"formDefinition\"}]"));

    connector.sendMessage(textToEmail("user@example.com", "Hello"), context);

    assertThat((String) captureRequest().getBody()).doesNotContain("formResourceKey");
  }

  // --- createChannel ---

  @Test
  void createChannel_success_returnsChannelIdAndVerifiesRequestBody() {
    doReturn(httpResponse(201, "{\"channelId\":\"19:new-channel@thread.tacv2\"}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var result = connector.createChannel(channelRequest("b7779302-e8cb-4b34-901b-5b150a19fd47"));

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

    connector.createChannel(
        channelRequest(
            "https://teams.cloud.microsoft/l/team/19%3Axxx?groupId=b7779302-e8cb-4b34-901b-5b150a19fd47&tenantId=abc"));

    assertThat((String) captureRequest().getBody())
        .contains("\"teamId\":\"b7779302-e8cb-4b34-901b-5b150a19fd47\"");
  }

  @Test
  void createChannel_blankMembershipType_defaultsToStandard() {
    doReturn(httpResponse(201, "{\"channelId\":\"19:new@thread.tacv2\"}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    connector.createChannel(
        new CreateChannelRequest("b7779302-e8cb-4b34-901b-5b150a19fd47", "My Channel", null, null));

    assertThat((String) captureRequest().getBody()).contains("\"membershipType\":\"standard\"");
  }

  @Test
  void createChannel_backendError_throwsConnectorException() {
    doThrow(new ConnectorException("500", "Internal Server Error"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = channelRequest("b7779302-e8cb-4b34-901b-5b150a19fd47");
    assertThatThrownBy(() -> connector.createChannel(request))
        .isInstanceOfSatisfying(
            ConnectorException.class, e -> assertThat(e.getErrorCode()).isEqualTo("500"));
  }

  @Test
  void createChannel_transportError_throwsConnectorException() {
    doThrow(new ConnectorException("IO_ERROR", "Connection refused"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = channelRequest("b7779302-e8cb-4b34-901b-5b150a19fd47");
    assertThatThrownBy(() -> connector.createChannel(request))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Connection refused");
  }

  /**
   * Without the {@code APP_INTEGRATIONS_*} variables the connector is unusable on this runtime.
   * Every job must fail immediately and raise an incident: a {@link ConnectorRetryException} with
   * {@code retries == 0}, rather than a plain {@link ConnectorException} that would first burn the
   * element template's retries at its configured backoff.
   */
  @Nested
  class NotConfigured {

    private void assertNotConfigured(Runnable invocation, String... messageFragments) {
      assertThatThrownBy(invocation::run)
          .isInstanceOfSatisfying(
              ConnectorRetryException.class,
              e -> {
                assertThat(e.getRetries()).isZero();
                assertThat(e.getErrorCode())
                    .isEqualTo(AppIntegrationsExecutor.NOT_CONFIGURED_ERROR_CODE);
                assertThat(e.getMessage()).contains("App Integrations are not configured");
                assertThat(e.getMessage()).contains(messageFragments);
              });
      verifyNoInteractions(httpClient);
    }

    @Test
    void emptyEnvironment_failsWithoutRetries() {
      var noEnv = connectorWith(Map.of());
      assertNotConfigured(
          () -> noEnv.sendMessage(textToEmail("user@example.com", "Hi"), context),
          "APP_INTEGRATIONS_BASE_URL");
    }

    @Test
    void baseUrlWithoutAnyAuth_namesBothAlternatives() {
      var partial = connectorWith(Map.of("APP_INTEGRATIONS_BASE_URL", BASE_URL));
      assertNotConfigured(
          () -> partial.sendMessage(textToEmail("user@example.com", "Hi"), context),
          "APP_INTEGRATIONS_API_KEY",
          "APP_INTEGRATIONS_OAUTH_TOKEN_ENDPOINT",
          "APP_INTEGRATIONS_OAUTH_CLIENT_ID",
          "APP_INTEGRATIONS_OAUTH_CLIENT_SECRET");
    }

    @Test
    void partiallyConfiguredOAuth_namesMissingVarsAndNeverLeaksTheSecret() {
      var partial =
          connectorWith(
              Map.of(
                  "APP_INTEGRATIONS_BASE_URL", BASE_URL,
                  "APP_INTEGRATIONS_OAUTH_TOKEN_ENDPOINT", TOKEN_ENDPOINT,
                  "APP_INTEGRATIONS_OAUTH_CLIENT_SECRET", "super-secret-value"));

      assertThatThrownBy(() -> partial.sendMessage(textToEmail("user@example.com", "Hi"), context))
          .isInstanceOfSatisfying(
              ConnectorRetryException.class,
              e -> {
                assertThat(e.getRetries()).isZero();
                assertThat(e.getMessage()).contains("APP_INTEGRATIONS_OAUTH_CLIENT_ID");
                // Incident messages are visible in Operate — credentials must never appear.
                assertThat(e.getMessage()).doesNotContain("super-secret-value");
              });
      verifyNoInteractions(httpClient);
    }

    @Test
    void blankValuesAreTreatedAsAbsent() {
      var blank =
          connectorWith(
              Map.of("APP_INTEGRATIONS_BASE_URL", BASE_URL, "APP_INTEGRATIONS_API_KEY", "   "));
      assertNotConfigured(
          () -> blank.sendMessage(textToEmail("user@example.com", "Hi"), context),
          "APP_INTEGRATIONS_API_KEY");
    }

    @Test
    void blankBaseUrl_failsWithoutRetries() {
      var blank =
          connectorWith(Map.of("APP_INTEGRATIONS_BASE_URL", "", "APP_INTEGRATIONS_API_KEY", "k"));
      assertNotConfigured(
          () -> blank.sendMessage(textToEmail("user@example.com", "Hi"), context),
          "APP_INTEGRATIONS_BASE_URL");
    }

    @Test
    void createChannel_isGatedTheSameWay() {
      // resolveConfig() sits on the shared post(...) path, so both operations behave identically.
      var noEnv = connectorWith(Map.of());
      assertNotConfigured(
          () -> noEnv.createChannel(channelRequest("b7779302-e8cb-4b34-901b-5b150a19fd47")),
          "APP_INTEGRATIONS_BASE_URL");
    }
  }
}
