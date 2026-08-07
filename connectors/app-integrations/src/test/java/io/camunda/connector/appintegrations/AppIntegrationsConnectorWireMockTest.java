/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.appintegrations.model.AdditionalContent;
import io.camunda.connector.appintegrations.model.ChannelPlatform;
import io.camunda.connector.appintegrations.model.CreateChannelRequest;
import io.camunda.connector.appintegrations.model.Recipient;
import io.camunda.connector.appintegrations.model.SendMessageRequest;
import io.camunda.connector.appintegrations.model.SlackTarget;
import io.camunda.connector.http.client.authentication.OAuthTokenCacheHolder;
import io.camunda.connector.http.client.authentication.cacheimpl.CaffeineOAuthTokenCache;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the connector against the real {@link
 * io.camunda.connector.http.client.client.apache.CustomApacheHttpClient} via WireMock, so the
 * production HTTP/OAuth contract is verified end-to-end (the SDK client throws on status {@code >=
 * 400}; the connector invalidates and retries the OAuth token on a 401). These complement the
 * mock-based unit tests, which cannot observe the real transport behaviour.
 */
@WireMockTest
class AppIntegrationsConnectorWireMockTest {

  private static final String CHANNEL_PATH = "/api/connector/channel";
  private static final String MESSAGE_PATH = "/api/connector/message";
  private static final String TOKEN_PATH = "/oauth/token";

  private final OutboundConnectorContext context = mock(OutboundConnectorContext.class);

  @BeforeEach
  void setUpContext() {
    var jobContext = mock(JobContext.class);
    when(context.getJobContext()).thenReturn(jobContext);
    when(jobContext.getCustomHeaders()).thenReturn(Map.of());
  }

  @BeforeEach
  @AfterEach
  void resetTokenCache() {
    // Start each test from a clean cache so the first OAuth fetch is always a miss, and don't leak
    // entries into other tests sharing the static holder.
    OAuthTokenCacheHolder.set(new CaffeineOAuthTokenCache());
  }

  /** All configuration comes from the environment, so every connector needs an env map. */
  private static AppIntegrationsConnector connectorWith(Map<String, String> env) {
    return new AppIntegrationsConnector(
        ConnectorsObjectMapperSupplier.getCopy(), new CustomApacheHttpClient(), env::get);
  }

  private static AppIntegrationsConnector apiKeyConnector(WireMockRuntimeInfo wm) {
    return connectorWith(
        Map.of(
            "APP_INTEGRATIONS_BASE_URL",
            wm.getHttpBaseUrl(),
            "APP_INTEGRATIONS_API_KEY",
            "test-key"));
  }

  private static AppIntegrationsConnector oauthConnector(WireMockRuntimeInfo wm) {
    return connectorWith(oauthEnv(wm));
  }

  private static Map<String, String> oauthEnv(WireMockRuntimeInfo wm) {
    return Map.of(
        "APP_INTEGRATIONS_BASE_URL", wm.getHttpBaseUrl(),
        "APP_INTEGRATIONS_OAUTH_TOKEN_ENDPOINT", wm.getHttpBaseUrl() + TOKEN_PATH,
        "APP_INTEGRATIONS_OAUTH_CLIENT_ID", "client-id",
        "APP_INTEGRATIONS_OAUTH_CLIENT_SECRET", "client-secret",
        "APP_INTEGRATIONS_OAUTH_AUDIENCE", "app-integrations");
  }

  private static AppIntegrationsConnector saasConnector(WireMockRuntimeInfo wm, String orgId) {
    var env = new HashMap<>(oauthEnv(wm));
    env.put("CAMUNDA_CONNECTOR_RUNTIME_SAAS", "true");
    env.put("CAMUNDA_CONNECTOR_CLOUD_ORGANIZATION_ID", orgId);
    env.put("CAMUNDA_CLIENT_CLOUD_CLUSTERID", "cluster-456");
    return connectorWith(env);
  }

  private static CreateChannelRequest channelRequest() {
    return new CreateChannelRequest(
        new ChannelPlatform.TeamsChannelPlatform(
            "My Channel", "b7779302-e8cb-4b34-901b-5b150a19fd47", "standard"),
        null);
  }

  private static void stubToken() {
    stubFor(
        post(urlPathEqualTo(TOKEN_PATH))
            .willReturn(
                okJson(
                    "{\"access_token\":\"tok\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")));
  }

  @Test
  void createChannel_realClient_success(WireMockRuntimeInfo wm) {
    stubFor(
        post(urlPathEqualTo(CHANNEL_PATH))
            .willReturn(okJson("{\"channelId\":\"19:new-channel@thread.tacv2\"}").withStatus(201)));

    var result = apiKeyConnector(wm).createChannel(channelRequest());

    assertThat(result.channelId()).isEqualTo("19:new-channel@thread.tacv2");
  }

  @Test
  void createChannel_realClient_serverError_throwsWithStatusCode(WireMockRuntimeInfo wm) {
    // Proves the crux of the design: the real SDK client THROWS on status >= 400 (it does not
    // return the response), with the status code as the connector exception's error code.
    stubFor(
        post(urlPathEqualTo(CHANNEL_PATH))
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

    var connector = apiKeyConnector(wm);

    assertThatThrownBy(() -> connector.createChannel(channelRequest()))
        .isInstanceOfSatisfying(
            ConnectorException.class, e -> assertThat(e.getErrorCode()).isEqualTo("500"));
  }

  @Test
  void createChannel_realClient_oauth401ThenSuccess_invalidatesAndRetries(WireMockRuntimeInfo wm) {
    stubToken();

    stubFor(
        post(urlPathEqualTo(CHANNEL_PATH))
            .inScenario("oauth-retry")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(401).withBody("Unauthorized"))
            .willSetStateTo("retried"));
    stubFor(
        post(urlPathEqualTo(CHANNEL_PATH))
            .inScenario("oauth-retry")
            .whenScenarioStateIs("retried")
            .willReturn(okJson("{\"channelId\":\"19:after-retry@thread.tacv2\"}").withStatus(201)));

    var result = oauthConnector(wm).createChannel(channelRequest());

    assertThat(result.channelId()).isEqualTo("19:after-retry@thread.tacv2");
    // The backend was called twice (the 401, then the successful retry) and the token was
    // re-fetched after invalidation (cache miss on the retry).
    verify(exactly(2), postRequestedFor(urlPathEqualTo(CHANNEL_PATH)));
    verify(exactly(2), postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
  }

  @Test
  void sendMessage_realClient_candidateUsersAndGroups_sentAsJsonArrays(WireMockRuntimeInfo wm) {
    stubFor(
        post(urlPathEqualTo(MESSAGE_PATH))
            .willReturn(okJson("{\"conversation\":\"conv-1\"}").withStatus(201)));

    var request =
        new SendMessageRequest(
            new Recipient.CamundaRecipient(
                "user@example.com",
                List.of("alice", "bob"),
                List.of("approvers"),
                new AdditionalContent.None()),
            "Please approve");

    var result = apiKeyConnector(wm).sendMessage(request, context);

    assertThat(result.conversation()).isEqualTo("conv-1");
    verify(
        postRequestedFor(urlPathEqualTo(MESSAGE_PATH))
            .withHeader("X-API-KEY", equalTo("test-key"))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "platform": "camunda",
                      "email": "user@example.com",
                      "candidateUsers": ["alice", "bob"],
                      "candidateGroups": ["approvers"],
                      "message": "Please approve"
                    }
                    """)));
  }

  @Test
  void sendMessage_realClient_slackMessageAndBlocks_sentAsRealJson(WireMockRuntimeInfo wm)
      throws Exception {
    stubFor(
        post(urlPathEqualTo(MESSAGE_PATH))
            .willReturn(okJson("{\"conversation\":\"conv-2\"}").withStatus(201)));

    var blocks =
        ConnectorsObjectMapperSupplier.getCopy()
            .readTree("[{\"type\":\"section\",\"text\":{\"type\":\"mrkdwn\",\"text\":\"hi\"}}]");
    var request =
        new SendMessageRequest(
            new Recipient.SlackRecipient(
                new SlackTarget.SlackChannelTarget("C0123456789"),
                new AdditionalContent.BlockKit(blocks)),
            "Deploy done");

    var result = apiKeyConnector(wm).sendMessage(request, context);

    assertThat(result.conversation()).isEqualTo("conv-2");
    // blocks must arrive as a real JSON array, not a string containing JSON.
    verify(
        postRequestedFor(urlPathEqualTo(MESSAGE_PATH))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "platform": "slack",
                      "channelId": "C0123456789",
                      "message": "Deploy done",
                      "blocks": [{"type": "section", "text": {"type": "mrkdwn", "text": "hi"}}]
                    }
                    """)));
  }

  @Test
  void saas_readsBaseUrlAndOAuthFromEnv(WireMockRuntimeInfo wm) {
    stubToken();
    stubFor(
        post(urlPathEqualTo(CHANNEL_PATH))
            .willReturn(okJson("{\"channelId\":\"19:saas@thread.tacv2\"}").withStatus(201)));

    var result = saasConnector(wm, "org-123").createChannel(channelRequest());

    assertThat(result.channelId()).isEqualTo("19:saas@thread.tacv2");
    verify(postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
    verify(
        postRequestedFor(urlPathEqualTo(CHANNEL_PATH))
            .withHeader("X-Org-Id", equalTo("org-123"))
            .withHeader("X-Cluster-Id", equalTo("cluster-456")));
  }

  @Test
  void saas_nullOrgSentinel_omitsOrgHeader(WireMockRuntimeInfo wm) {
    stubToken();
    stubFor(
        post(urlPathEqualTo(CHANNEL_PATH))
            .willReturn(okJson("{\"channelId\":\"19:saas@thread.tacv2\"}").withStatus(201)));

    saasConnector(wm, "null").createChannel(channelRequest());

    verify(postRequestedFor(urlPathEqualTo(CHANNEL_PATH)).withHeader("X-Org-Id", absent()));
  }
}
