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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.appintegrations.model.AdditionalContent;
import io.camunda.connector.appintegrations.model.CamundaExtra;
import io.camunda.connector.appintegrations.model.ChannelPlatform;
import io.camunda.connector.appintegrations.model.CreateChannelRequest;
import io.camunda.connector.appintegrations.model.CreateChannelResult;
import io.camunda.connector.appintegrations.model.Recipient;
import io.camunda.connector.appintegrations.model.SendMessageRequest;
import io.camunda.connector.appintegrations.model.SendMessageResult;
import io.camunda.connector.appintegrations.model.SlackExtra;
import io.camunda.connector.appintegrations.model.SlackTarget;
import io.camunda.connector.appintegrations.model.TeamsExtra;
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
  private static final String CARD = "{\"type\":\"AdaptiveCard\",\"version\":\"1.5\"}";
  private static final String BLOCKS = "[{\"type\":\"section\"}]";

  private static final Map<String, String> API_KEY_ENV =
      Map.of("APP_INTEGRATIONS_BASE_URL", BASE_URL, "APP_INTEGRATIONS_API_KEY", "test-key");

  private static final Map<String, String> OAUTH_ENV =
      Map.of(
          "APP_INTEGRATIONS_BASE_URL", BASE_URL,
          "APP_INTEGRATIONS_OAUTH_TOKEN_ENDPOINT", TOKEN_ENDPOINT,
          "APP_INTEGRATIONS_OAUTH_CLIENT_ID", "client-id",
          "APP_INTEGRATIONS_OAUTH_CLIENT_SECRET", "client-secret",
          "APP_INTEGRATIONS_OAUTH_AUDIENCE", "app-integrations");

  private static final ObjectMapper MAPPER = new ObjectMapper();

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
    OAuthTokenCacheHolder.set(new CaffeineOAuthTokenCache());
  }

  private AppIntegrationsConnector connectorWith(Map<String, String> env) {
    return new AppIntegrationsConnector(MAPPER, httpClient, env::get);
  }

  private static JsonNode json(String raw) {
    try {
      return MAPPER.readTree(raw);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
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

  private String captureBody() {
    return (String) captureRequest().getBody();
  }

  // --- request builders ---

  private static SendMessageRequest camunda(String email, String message, CamundaExtra extra) {
    return new SendMessageRequest(
        new Recipient.CamundaRecipient(email, null, null, extra), message);
  }

  private static SendMessageRequest teams(String channelId, String message, TeamsExtra extra) {
    return new SendMessageRequest(new Recipient.TeamsRecipient(channelId, extra), message);
  }

  private static SendMessageRequest slack(SlackTarget target, String message, SlackExtra extra) {
    return new SendMessageRequest(new Recipient.SlackRecipient(target, extra), message);
  }

  private static SendMessageRequest camundaText(String email, String message) {
    return camunda(email, message, new AdditionalContent.None());
  }

  private static CreateChannelRequest teamsChannel(String teamId) {
    return new CreateChannelRequest(
        new ChannelPlatform.TeamsChannelPlatform("My Channel", teamId, "standard"), null);
  }

  // --- payload shape per recipient / additional content ---

  @Test
  void camunda_plainMessage_sendsPlatformAndEmailOnly() {
    stubOk();

    connector.sendMessage(camundaText("user@example.com", "Please review"), context);

    var body = captureBody();
    assertThat(body).contains("\"platform\":\"camunda\"");
    assertThat(body).contains("\"email\":\"user@example.com\"");
    assertThat(body).contains("\"message\":\"Please review\"");
    assertThat(body)
        .doesNotContain("channelId", "userId", "adaptiveCard", "blocks", "formResourceKey");
  }

  @Test
  void camunda_candidateUsersAndGroups_sentAsJsonArrays() {
    stubOk();

    var request =
        new SendMessageRequest(
            new Recipient.CamundaRecipient(
                null, List.of("alice", "bob"), List.of("approvers"), new AdditionalContent.None()),
            "Please approve");
    connector.sendMessage(request, context);

    var body = captureBody();
    assertThat(body).contains("\"candidateUsers\":[\"alice\",\"bob\"]");
    assertThat(body).contains("\"candidateGroups\":[\"approvers\"]");
    assertThat(body).doesNotContain("\"email\"");
  }

  @Test
  void teams_messageAndAdaptiveCardTogether_sendsBoth() {
    // The headline of this change: text and rich content are no longer mutually exclusive.
    stubOk();

    connector.sendMessage(
        teams("19:abc@thread.tacv2", "Deploy done", new AdditionalContent.AdaptiveCard(json(CARD))),
        context);

    var body = captureBody();
    assertThat(body).contains("\"platform\":\"teams\"");
    assertThat(body).contains("\"channelId\":\"19:abc@thread.tacv2\"");
    assertThat(body).contains("\"message\":\"Deploy done\"");
    // Sent as real JSON, not a string.
    assertThat(body).contains("\"adaptiveCard\":{\"type\":\"AdaptiveCard\"");
    assertThat(body).doesNotContain("blocks", "formResourceKey", "email");
  }

  @Test
  void slack_channel_messageAndBlocksTogether_sendsBoth() {
    stubOk();

    connector.sendMessage(
        slack(
            new SlackTarget.SlackChannelTarget("C0123456789"),
            "Deploy done",
            new AdditionalContent.BlockKit(json(BLOCKS))),
        context);

    var body = captureBody();
    assertThat(body).contains("\"platform\":\"slack\"");
    assertThat(body).contains("\"channelId\":\"C0123456789\"");
    assertThat(body).contains("\"message\":\"Deploy done\"");
    assertThat(body).contains("\"blocks\":[{\"type\":\"section\"}]");
    assertThat(body).doesNotContain("adaptiveCard", "userId", "formResourceKey");
  }

  @Test
  void slack_user_sendsUserIdNotChannelId() {
    stubOk();

    connector.sendMessage(
        slack(new SlackTarget.SlackUserTarget("U0123456789"), "Ping", new AdditionalContent.None()),
        context);

    var body = captureBody();
    assertThat(body).contains("\"platform\":\"slack\"");
    assertThat(body).contains("\"userId\":\"U0123456789\"");
    assertThat(body).doesNotContain("channelId");
  }

  @Test
  void additionalContentOnly_omitsMessageEntirely() {
    stubOk();

    connector.sendMessage(
        teams("19:abc@thread.tacv2", "  ", new AdditionalContent.AdaptiveCard(json(CARD))),
        context);

    var body = captureBody();
    assertThat(body).doesNotContain("\"message\"");
    assertThat(body).contains("\"adaptiveCard\"");
  }

  @Test
  void nothingToSend_failsValidation() {
    var request = teams("19:abc@thread.tacv2", "   ", new AdditionalContent.None());

    assertThat(VALIDATOR.validate(request))
        .anyMatch(v -> v.getMessage().contains("Provide a message, additional content, or both"));
  }

  // --- JSON normalisation ---

  @Test
  void adaptiveCard_arrivingAsJsonString_isParsedIntoAnObject() {
    // Belt and braces: the property is FEEL-enabled so the engine normally hands over a real
    // object,
    // but a static paste that was never evaluated must still reach the backend as JSON.
    stubOk();

    connector.sendMessage(
        teams(
            "19:abc@thread.tacv2",
            null,
            new AdditionalContent.AdaptiveCard(MAPPER.getNodeFactory().textNode(CARD))),
        context);

    assertThat(captureBody()).contains("\"adaptiveCard\":{\"type\":\"AdaptiveCard\"");
  }

  @Test
  void adaptiveCard_malformedJsonString_throwsValidationError() {
    var request =
        teams(
            "19:abc@thread.tacv2",
            null,
            new AdditionalContent.AdaptiveCard(MAPPER.getNodeFactory().textNode("{not json")));

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("VALIDATION_ERROR");
              assertThat(e.getMessage()).contains("'adaptiveCard' is not valid JSON");
            });
    verifyNoInteractions(httpClient);
  }

  @Test
  void adaptiveCard_wrongShape_throwsValidationError() {
    var request =
        teams("19:abc@thread.tacv2", null, new AdditionalContent.AdaptiveCard(json(BLOCKS)));

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("'adaptiveCard' must be a JSON object");
    verifyNoInteractions(httpClient);
  }

  @Test
  void blocks_wrongShape_throwsValidationError() {
    var request =
        slack(
            new SlackTarget.SlackChannelTarget("C1"),
            null,
            new AdditionalContent.BlockKit(json(CARD)));

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("'blocks' must be a JSON array");
    verifyNoInteractions(httpClient);
  }

  // --- validation ---

  @Test
  void camundaRecipient_withOnlyBlankCandidates_failsValidation() {
    // An element-level @NotBlank plus blank filtering: a list of blanks identifies nobody, so it
    // must
    // neither pass validation nor be sent as a recipient.
    var request =
        new SendMessageRequest(
            new Recipient.CamundaRecipient(
                null, List.of("", "  "), null, new AdditionalContent.None()),
            "Hello");

    assertThat(VALIDATOR.validate(request)).isNotEmpty();
  }

  @Test
  void camundaRecipient_blankCandidateEntriesAreFilteredOut() {
    stubOk();

    var request =
        new SendMessageRequest(
            new Recipient.CamundaRecipient(
                "user@example.com",
                List.of("alice", "  "),
                List.of("  "),
                new AdditionalContent.None()),
            "Hello");
    connector.sendMessage(request, context);

    var body = captureBody();
    assertThat(body).contains("\"candidateUsers\":[\"alice\"]");
    assertThat(body).doesNotContain("candidateGroups");
  }

  @Test
  void camundaRecipient_withNothingProvided_failsValidation() {
    var request = camundaText(null, "Hello");

    assertThat(VALIDATOR.validate(request))
        .anyMatch(v -> v.getMessage().contains("At least one of 'email'"));
  }

  @Test
  void whitespaceOnlyRecipients_failValidation() {
    // @NotEmpty would accept these; the executor then normalises them to null and the payload would
    // carry no recipient identifier at all. @NotBlank is what actually stops that.
    assertThat(VALIDATOR.validate(teams("   ", "Hello", new AdditionalContent.None())))
        .isNotEmpty();
    assertThat(
            VALIDATOR.validate(
                slack(
                    new SlackTarget.SlackChannelTarget("  "),
                    "Hello",
                    new AdditionalContent.None())))
        .isNotEmpty();
    assertThat(
            VALIDATOR.validate(
                slack(
                    new SlackTarget.SlackUserTarget("\t"), "Hello", new AdditionalContent.None())))
        .isNotEmpty();
  }

  @Test
  void whitespaceOnlyChannelFields_failValidation() {
    assertThat(
            VALIDATOR.validate(
                new CreateChannelRequest(
                    new ChannelPlatform.TeamsChannelPlatform("  ", "g-1", "standard"), null)))
        .isNotEmpty();
    assertThat(
            VALIDATOR.validate(
                new CreateChannelRequest(
                    new ChannelPlatform.TeamsChannelPlatform("My Channel", "  ", "standard"),
                    null)))
        .isNotEmpty();
  }

  @Test
  void slackChannelTarget_blank_failsValidation() {
    var request =
        slack(new SlackTarget.SlackChannelTarget(""), "Hello", new AdditionalContent.None());

    assertThat(VALIDATOR.validate(request)).isNotEmpty();
  }

  @Test
  void slackUserTarget_blank_failsValidation() {
    var request = slack(new SlackTarget.SlackUserTarget(""), "Hello", new AdditionalContent.None());

    assertThat(VALIDATOR.validate(request)).isNotEmpty();
  }

  @Test
  void nullNestedAdditionalContent_failsValidation() {
    var request = new SendMessageRequest(new Recipient.TeamsRecipient("19:abc", null), "Hello");

    assertThat(VALIDATOR.validate(request)).isNotEmpty();
  }

  // --- linked form, per branch ---

  private static final String FORM_HEADER =
      "[{\"resourceKey\":\"12345\",\"resourceType\":\"form\",\"linkName\":\"formTeams\"}]";

  private void stubFormHeader() {
    when(jobContext.getCustomHeaders()).thenReturn(Map.of("linkedResources", FORM_HEADER));
  }

  @Test
  void form_camundaBranch_includesFormResourceKey() {
    stubOk();
    stubFormHeader();

    connector.sendMessage(
        camunda("user@example.com", "Please approve", new AdditionalContent.Form()), context);

    var body = captureBody();
    assertThat(body).contains("\"formResourceKey\":\"12345\"");
    assertThat(body).contains("\"platform\":\"camunda\"");
  }

  @Test
  void form_teamsBranch_includesFormResourceKey() {
    stubOk();
    stubFormHeader();

    connector.sendMessage(teams("19:abc", null, new AdditionalContent.Form()), context);

    assertThat(captureBody()).contains("\"formResourceKey\":\"12345\"");
  }

  @Test
  void form_slackBranch_includesFormResourceKey() {
    stubOk();
    stubFormHeader();

    connector.sendMessage(
        slack(new SlackTarget.SlackChannelTarget("C1"), null, new AdditionalContent.Form()),
        context);

    assertThat(captureBody()).contains("\"formResourceKey\":\"12345\"");
  }

  @Test
  void form_withoutLinkedForm_throwsValidationError() {
    var request = teams("19:abc", null, new AdditionalContent.Form());

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining(
            "Additional content is 'form' but no linked form was found on the job");
    verifyNoInteractions(httpClient);
  }

  @Test
  void form_malformedLinkedResourcesHeader_throwsValidationError() {
    when(jobContext.getCustomHeaders()).thenReturn(Map.of("linkedResources", "not-valid-json"));
    var request = teams("19:abc", null, new AdditionalContent.Form());

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("no linked form was found");
    verifyNoInteractions(httpClient);
  }

  @Test
  void nonFormSelection_ignoresLinkedResourcesHeader() {
    stubOk();
    stubFormHeader();

    connector.sendMessage(teams("19:abc", "Hello", new AdditionalContent.None()), context);

    assertThat(captureBody()).doesNotContain("formResourceKey");
  }

  // --- authentication and transport ---

  @Test
  void sendMessage_withOAuth_delegatesOAuthToHttpClient() {
    stubOk();

    connectorWith(OAUTH_ENV).sendMessage(camundaText("user@example.com", "Hi"), context);

    var sent = captureRequest();
    assertThat(sent.getHeader("Authorization")).isEmpty();
    assertThat(sent.getHeader("X-API-KEY")).isEmpty();
    assertThat(sent.getAuthentication())
        .isInstanceOf(io.camunda.connector.http.client.model.auth.OAuthAuthentication.class);
    var oauth =
        (io.camunda.connector.http.client.model.auth.OAuthAuthentication) sent.getAuthentication();
    assertThat(oauth.clientId()).isEqualTo("client-id");
    assertThat(oauth.oauthTokenEndpoint()).isEqualTo(TOKEN_ENDPOINT);
  }

  @Test
  void oauthAndApiKeyBothConfigured_oauthWins() {
    stubOk();
    var env = new java.util.HashMap<>(OAUTH_ENV);
    env.put("APP_INTEGRATIONS_API_KEY", "unused-key");

    connectorWith(env).sendMessage(camundaText("user@example.com", "Hi"), context);

    assertThat(captureRequest().getHeader("X-API-KEY")).isEmpty();
  }

  @Test
  void sendMessage_oauth401_invalidatesTokenAndRetries() {
    OAuthTokenCacheHolder.set(tokenCache);
    doThrow(new ConnectorException("401", "Unauthorized"))
        .doReturn(httpResponse(201, "{\"conversation\":null}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var result =
        connectorWith(OAUTH_ENV).sendMessage(camundaText("user@example.com", "Hi"), context);

    assertThat(result.conversation()).isNull();
    verify(tokenCache)
        .invalidate(any(io.camunda.connector.http.client.model.auth.OAuthAuthentication.class));
    verify(httpClient, times(2)).execute(any(HttpClientRequest.class), any());
  }

  @Test
  void sendMessage_apiKey401_propagatesWithoutRetry() {
    doThrow(new ConnectorException("401", "Unauthorized"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = camundaText("user@example.com", "Hi");

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOfSatisfying(
            ConnectorException.class, e -> assertThat(e.getErrorCode()).isEqualTo("401"));
    verify(httpClient, times(1)).execute(any(HttpClientRequest.class), any());
  }

  @Test
  void sendMessage_notSaas_omitsContextHeaders() {
    stubOk();

    connector.sendMessage(camundaText("user@example.com", "Hi"), context);

    var req = captureRequest();
    assertThat(req.getHeader("X-Org-Id")).isEmpty();
    assertThat(req.getHeader("X-Cluster-Id")).isEmpty();
  }

  @Test
  void sendMessage_byEmail_callsCorrectEndpointWithApiKeyHeader() {
    stubOk();

    var result = connector.sendMessage(camundaText("user@example.com", "Hello"), context);

    assertThat(result).isInstanceOf(SendMessageResult.class);
    var req = captureRequest();
    assertThat(req.getUrl()).endsWith("/api/connector/message");
    assertThat(req.getHeader("X-API-KEY")).hasValue("test-key");
  }

  @Test
  void sendMessage_backendError_throwsConnectorException() {
    doThrow(new ConnectorException("500", "Internal Server Error"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = camundaText("user@example.com", "Hello");

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOfSatisfying(
            ConnectorException.class, e -> assertThat(e.getErrorCode()).isEqualTo("500"));
  }

  @Test
  void sendMessage_transportError_throwsConnectorException() {
    doThrow(new ConnectorException("IO_ERROR", "Connection refused"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = camundaText("user@example.com", "Hello");

    assertThatThrownBy(() -> connector.sendMessage(request, context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Connection refused");
  }

  // --- createChannel ---

  @Test
  void createChannel_teams_success() {
    doReturn(httpResponse(201, "{\"channelId\":\"19:new-channel@thread.tacv2\"}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var result = connector.createChannel(teamsChannel("b7779302-e8cb-4b34-901b-5b150a19fd47"));

    assertThat(result).isInstanceOf(CreateChannelResult.class);
    assertThat(result.channelId()).isEqualTo("19:new-channel@thread.tacv2");

    var req = captureRequest();
    var body = (String) req.getBody();
    assertThat(body).contains("\"platform\":\"teams\"");
    assertThat(body).contains("\"teamId\":\"b7779302-e8cb-4b34-901b-5b150a19fd47\"");
    assertThat(body).contains("\"displayName\":\"My Channel\"");
    assertThat(body).contains("\"membershipType\":\"standard\"");
    assertThat(body).doesNotContain("description", "workspaceId", "isPrivate");
    assertThat(req.getUrl()).endsWith("/api/connector/channel");
  }

  @Test
  void createChannel_teamsUrl_extractsGroupIdBeforeSending() {
    doReturn(httpResponse(201, "{\"channelId\":\"19:new@thread.tacv2\"}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    connector.createChannel(
        teamsChannel(
            "https://teams.cloud.microsoft/l/team/19%3Axxx?groupId=b7779302-e8cb-4b34-901b-5b150a19fd47&tenantId=abc"));

    assertThat(captureBody()).contains("\"teamId\":\"b7779302-e8cb-4b34-901b-5b150a19fd47\"");
  }

  @Test
  void createChannel_blankMembershipType_defaultsToStandard() {
    doReturn(httpResponse(201, "{\"channelId\":\"19:new@thread.tacv2\"}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    connector.createChannel(
        new CreateChannelRequest(
            new ChannelPlatform.TeamsChannelPlatform("My Channel", "group-1", null), null));

    assertThat(captureBody()).contains("\"membershipType\":\"standard\"");
  }

  @Test
  void createChannel_slack_sendsWorkspaceAndPrivacyAndNoTeamsFields() {
    doReturn(httpResponse(201, "{\"channelId\":\"C0999\"}"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var result =
        connector.createChannel(
            new CreateChannelRequest(
                new ChannelPlatform.SlackChannelPlatform("releases", "T0123", true),
                "Automated releases"));

    assertThat(result.channelId()).isEqualTo("C0999");
    var body = captureBody();
    assertThat(body).contains("\"platform\":\"slack\"");
    assertThat(body).contains("\"workspaceId\":\"T0123\"");
    assertThat(body).contains("\"isPrivate\":true");
    assertThat(body).contains("\"description\":\"Automated releases\"");
    assertThat(body).doesNotContain("teamId", "membershipType");
  }

  @Test
  void createChannel_teamsNameOverFiftyChars_failsValidation() {
    // The limits are per platform, and each is declared on its own subtype so Modeler shows the
    // right maxLength rather than advertising the laxer one to both.
    var request =
        new CreateChannelRequest(
            new ChannelPlatform.TeamsChannelPlatform("x".repeat(51), "group-1", "standard"), null);

    assertThat(VALIDATOR.validate(request)).isNotEmpty();
  }

  @Test
  void createChannel_slackNameOfFiftyOneChars_isAccepted() {
    var request =
        new CreateChannelRequest(
            new ChannelPlatform.SlackChannelPlatform("x".repeat(51), null, false), null);

    assertThat(VALIDATOR.validate(request)).isEmpty();
  }

  @Test
  void createChannel_slackNameOverEightyChars_failsValidation() {
    var request =
        new CreateChannelRequest(
            new ChannelPlatform.SlackChannelPlatform("x".repeat(81), null, false), null);

    assertThat(VALIDATOR.validate(request)).isNotEmpty();
  }

  @Test
  void createChannel_slackNameWithSpacesOrUppercase_failsValidation() {
    // Slack rejects these server-side; catching it here keeps the incident actionable.
    assertThat(
            VALIDATOR.validate(
                new CreateChannelRequest(
                    new ChannelPlatform.SlackChannelPlatform("My Channel", null, false), null)))
        .anyMatch(v -> v.getMessage().contains("lowercase letters"));
  }

  @Test
  void createChannel_backendError_throwsConnectorException() {
    doThrow(new ConnectorException("500", "Internal Server Error"))
        .when(httpClient)
        .execute(any(HttpClientRequest.class), any());

    var request = teamsChannel("group-1");
    assertThatThrownBy(() -> connector.createChannel(request))
        .isInstanceOfSatisfying(
            ConnectorException.class, e -> assertThat(e.getErrorCode()).isEqualTo("500"));
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
          () -> noEnv.sendMessage(camundaText("user@example.com", "Hi"), context),
          "APP_INTEGRATIONS_BASE_URL");
    }

    @Test
    void baseUrlWithoutAnyAuth_namesBothAlternatives() {
      var partial = connectorWith(Map.of("APP_INTEGRATIONS_BASE_URL", BASE_URL));
      assertNotConfigured(
          () -> partial.sendMessage(camundaText("user@example.com", "Hi"), context),
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

      assertThatThrownBy(() -> partial.sendMessage(camundaText("user@example.com", "Hi"), context))
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
          () -> blank.sendMessage(camundaText("user@example.com", "Hi"), context),
          "APP_INTEGRATIONS_API_KEY");
    }

    @Test
    void createChannel_isGatedTheSameWay() {
      var noEnv = connectorWith(Map.of());
      assertNotConfigured(
          () -> noEnv.createChannel(teamsChannel("group-1")), "APP_INTEGRATIONS_BASE_URL");
    }
  }
}
