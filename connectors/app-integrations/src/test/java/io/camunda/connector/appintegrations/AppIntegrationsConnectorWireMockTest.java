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
import io.camunda.connector.appintegrations.model.SendMessageResult;
import io.camunda.connector.appintegrations.model.SlackTarget;
import io.camunda.connector.appintegrations.model.TeamsTarget;
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
  private static final String CLUSTER_ID = "cluster-789";
  private static final String CLOUD_CLUSTER_ID = "cluster-456";

  private final OutboundConnectorContext context = mock(OutboundConnectorContext.class);
  private final JobContext jobContext = mock(JobContext.class);

  @BeforeEach
  void setUpContext() {
    when(context.getJobContext()).thenReturn(jobContext);
    when(jobContext.getCustomHeaders()).thenReturn(Map.of());
    when(jobContext.getBpmnProcessId()).thenReturn("order-process");
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
    var env = new HashMap<>(oauthEnv(wm));
    env.put("APP_INTEGRATIONS_CLUSTER_ID", CLUSTER_ID);
    return connectorWith(env);
  }

  /**
   * The OAuth credentials alone. The cluster id is deliberately left out, because SaaS and
   * Self-Managed supply it through different variables.
   */
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
    env.put("CAMUNDA_CLIENT_CLOUD_CLUSTERID", CLOUD_CLUSTER_ID);
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

  private static void stubChannel() {
    stubFor(
        post(urlPathEqualTo(CHANNEL_PATH))
            .willReturn(okJson("{\"channelId\":\"19:new-channel@thread.tacv2\"}").withStatus(201)));
  }

  private static void stubMessage() {
    stubFor(
        post(urlPathEqualTo(MESSAGE_PATH))
            .willReturn(
                okJson(
                        "{\"deliveries\":[{\"platform\":\"teams\",\"conversation\":\"conv-1\",\"messageId\":\"m-1\",\"conversationKey\":\"teams:conv-1\"}],\"failures\":[]}")
                    .withStatus(201)));
  }

  private static SendMessageRequest messageRequest() {
    return new SendMessageRequest(
        new Recipient.CamundaRecipient(
            "user@example.com", null, null, new AdditionalContent.None()),
        "Please approve");
  }

  @Test
  void createChannel_realClient_success(WireMockRuntimeInfo wm) {
    stubChannel();

    var result = apiKeyConnector(wm).createChannel(channelRequest(), context);

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

    assertThatThrownBy(() -> connector.createChannel(channelRequest(), context))
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

    var result = oauthConnector(wm).createChannel(channelRequest(), context);

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
            .willReturn(
                okJson(
                        """
                        {"deliveries":[{"platform":"slack","conversation":"D0123","messageId":"1712345678.000100",
                                        "conversationKey":"slack:D0123:1712345678.000100"},
                                       {"platform":"teams","conversation":"conv-2","messageId":"m-2",
                                        "conversationKey":"teams:conv-2"}],
                         "failures":[{"platform":"slack","conversation":"C0999","reason":"not_in_channel"}]}""")
                    .withStatus(201)));

    var request =
        new SendMessageRequest(
            new Recipient.CamundaRecipient(
                "user@example.com",
                List.of("alice", "bob"),
                List.of("approvers"),
                new AdditionalContent.None()),
            "Please approve");

    var result = apiKeyConnector(wm).sendMessage(request, context);

    assertThat(result.deliveries())
        .extracting(SendMessageResult.Delivery::conversation)
        .containsExactly("D0123", "conv-2");
    assertThat(result.deliveries())
        .extracting(SendMessageResult.Delivery::conversationKey)
        .containsExactly("slack:D0123:1712345678.000100", "teams:conv-2");
    assertThat(result.failures())
        .singleElement()
        .satisfies(failure -> assertThat(failure.reason()).isEqualTo("not_in_channel"));
    verify(
        postRequestedFor(urlPathEqualTo(MESSAGE_PATH))
            .withHeader("X-API-KEY", equalTo("test-key"))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "platform": "camunda",
                      "processDefinitionId": "order-process",
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
            .willReturn(
                okJson(
                        "{\"deliveries\":[{\"platform\":\"slack\",\"conversation\":\"C0123456789\",\"messageId\":\"1712345678.000200\",\"conversationKey\":\"slack:C0123456789:1712345678.000200\"}],\"failures\":[]}")
                    .withStatus(201)));

    var blocks =
        ConnectorsObjectMapperSupplier.getCopy()
            .readTree("[{\"type\":\"section\",\"text\":{\"type\":\"mrkdwn\",\"text\":\"hi\"}}]");
    var request =
        new SendMessageRequest(
            new Recipient.SlackRecipient(
                new SlackTarget.SlackChannelTarget("C0123456789"),
                "1712345678.000100",
                new AdditionalContent.BlockKit(blocks)),
            "Deploy done");

    var result = apiKeyConnector(wm).sendMessage(request, context);

    assertThat(result.deliveries())
        .singleElement()
        .satisfies(delivery -> assertThat(delivery.messageId()).isEqualTo("1712345678.000200"));
    // blocks must arrive as a real JSON array, not a string containing JSON.
    verify(
        postRequestedFor(urlPathEqualTo(MESSAGE_PATH))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "platform": "slack",
                      "processDefinitionId": "order-process",
                      "channelId": "C0123456789",
                      "threadTs": "1712345678.000100",
                      "message": "Deploy done",
                      "blocks": [{"type": "section", "text": {"type": "mrkdwn", "text": "hi"}}]
                    }
                    """)));
  }

  @Test
  void sendMessage_realClient_teamsConversationTarget_sentAsConversationId(WireMockRuntimeInfo wm) {
    stubFor(
        post(urlPathEqualTo(MESSAGE_PATH))
            .willReturn(
                okJson(
                        "{\"deliveries\":[{\"platform\":\"teams\",\"conversation\":\"19:abc@thread.tacv2;messageid=17123456789\",\"messageId\":\"17123456790\",\"conversationKey\":\"teams:19:abc@thread.tacv2;messageid=17123456789\"}],\"failures\":[]}")
                    .withStatus(201)));

    var request =
        new SendMessageRequest(
            new Recipient.TeamsRecipient(
                new TeamsTarget.TeamsConversationTarget(
                    "19:abc@thread.tacv2;messageid=17123456789"),
                new AdditionalContent.None()),
            "Following up");

    var result = apiKeyConnector(wm).sendMessage(request, context);

    assertThat(result.deliveries())
        .singleElement()
        .satisfies(delivery -> assertThat(delivery.messageId()).isEqualTo("17123456790"));
    verify(
        postRequestedFor(urlPathEqualTo(MESSAGE_PATH))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "platform": "teams",
                      "processDefinitionId": "order-process",
                      "conversationId": "19:abc@thread.tacv2;messageid=17123456789",
                      "message": "Following up"
                    }
                    """)));
  }

  @Test
  void saas_readsBaseUrlAndOAuthFromEnv(WireMockRuntimeInfo wm) {
    stubToken();
    stubFor(
        post(urlPathEqualTo(CHANNEL_PATH))
            .willReturn(okJson("{\"channelId\":\"19:saas@thread.tacv2\"}").withStatus(201)));

    var result = saasConnector(wm, "org-123").createChannel(channelRequest(), context);

    assertThat(result.channelId()).isEqualTo("19:saas@thread.tacv2");
    verify(postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
    verify(
        postRequestedFor(urlPathEqualTo(CHANNEL_PATH))
            .withHeader("X-Org-Id", equalTo("org-123"))
            .withHeader("X-Cluster-Id", equalTo(CLOUD_CLUSTER_ID)));
  }

  @Test
  void saas_nullOrgSentinel_omitsOrgHeader(WireMockRuntimeInfo wm) {
    stubToken();
    stubFor(
        post(urlPathEqualTo(CHANNEL_PATH))
            .willReturn(okJson("{\"channelId\":\"19:saas@thread.tacv2\"}").withStatus(201)));

    saasConnector(wm, "null").createChannel(channelRequest(), context);

    verify(postRequestedFor(urlPathEqualTo(CHANNEL_PATH)).withHeader("X-Org-Id", absent()));
  }

  // --- context identification headers ---

  @Test
  void selfManagedOAuth_sendsClusterIdAndNoOrgId(WireMockRuntimeInfo wm) {
    stubToken();
    stubChannel();

    oauthConnector(wm).createChannel(channelRequest(), context);

    verify(
        postRequestedFor(urlPathEqualTo(CHANNEL_PATH))
            .withHeader("X-Cluster-Id", equalTo(CLUSTER_ID))
            .withHeader("X-Org-Id", absent()));
  }

  @Test
  void appIntegrationsClusterId_winsOverTheCloudOne(WireMockRuntimeInfo wm) {
    stubToken();
    stubChannel();

    var env = new HashMap<>(oauthEnv(wm));
    env.put("CAMUNDA_CLIENT_CLOUD_CLUSTERID", CLOUD_CLUSTER_ID);
    env.put("APP_INTEGRATIONS_CLUSTER_ID", CLUSTER_ID);

    connectorWith(env).createChannel(channelRequest(), context);

    verify(
        postRequestedFor(urlPathEqualTo(CHANNEL_PATH))
            .withHeader("X-Cluster-Id", equalTo(CLUSTER_ID)));
  }

  @Test
  void oauthWithoutAnyClusterId_failsWithoutCallingTheBackend(WireMockRuntimeInfo wm) {
    stubToken();
    stubChannel();

    var connector = connectorWith(oauthEnv(wm));

    assertThatThrownBy(() -> connector.createChannel(channelRequest(), context))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode())
                  .isEqualTo(AppIntegrationsExecutor.NOT_CONFIGURED_ERROR_CODE);
              assertThat(e.getMessage()).contains("APP_INTEGRATIONS_CLUSTER_ID");
            });
    verify(exactly(0), postRequestedFor(urlPathEqualTo(CHANNEL_PATH)));
  }

  @Test
  void apiKeyWithoutClusterId_stillCallsTheBackend(WireMockRuntimeInfo wm) {
    // The backend recovers the cluster from the API key itself, so it stays optional on this path.
    stubChannel();

    apiKeyConnector(wm).createChannel(channelRequest(), context);

    verify(postRequestedFor(urlPathEqualTo(CHANNEL_PATH)).withHeader("X-Cluster-Id", absent()));
  }

  @Test
  void physicalTenantOnTheJob_isSentAsHeader(WireMockRuntimeInfo wm) {
    when(jobContext.getPhysicalTenantId()).thenReturn("tenanta");
    stubMessage();

    apiKeyConnector(wm).sendMessage(messageRequest(), context);

    verify(
        postRequestedFor(urlPathEqualTo(MESSAGE_PATH))
            .withHeader("X-Physical-Tenant-Id", equalTo("tenanta")));
  }

  @Test
  void defaultPhysicalTenant_isSentVerbatim(WireMockRuntimeInfo wm) {
    // "default" is the broker's canonical physical tenant and the backend accepts it literally, so
    // there is nothing to strip.
    when(jobContext.getPhysicalTenantId()).thenReturn("default");
    stubMessage();

    apiKeyConnector(wm).sendMessage(messageRequest(), context);

    verify(
        postRequestedFor(urlPathEqualTo(MESSAGE_PATH))
            .withHeader("X-Physical-Tenant-Id", equalTo("default")));
  }

  @Test
  void noPhysicalTenantOnTheJob_omitsTheHeader(WireMockRuntimeInfo wm) {
    when(jobContext.getPhysicalTenantId()).thenReturn(null);
    stubMessage();

    apiKeyConnector(wm).sendMessage(messageRequest(), context);

    verify(
        postRequestedFor(urlPathEqualTo(MESSAGE_PATH))
            .withHeader("X-Physical-Tenant-Id", absent()));
  }

  @Test
  void logicalTenant_isNeverSentAsThePhysicalTenant(WireMockRuntimeInfo wm) {
    // The two are different concepts, and the backend rejects the logical default "<default>".
    when(jobContext.getTenantId()).thenReturn("<default>");
    when(jobContext.getPhysicalTenantId()).thenReturn(null);
    stubMessage();

    apiKeyConnector(wm).sendMessage(messageRequest(), context);

    verify(
        postRequestedFor(urlPathEqualTo(MESSAGE_PATH))
            .withHeader("X-Physical-Tenant-Id", absent()));
  }

  @Test
  void createChannel_propagatesThePhysicalTenant(WireMockRuntimeInfo wm) {
    when(jobContext.getPhysicalTenantId()).thenReturn("tenantb");
    stubChannel();

    apiKeyConnector(wm).createChannel(channelRequest(), context);

    verify(
        postRequestedFor(urlPathEqualTo(CHANNEL_PATH))
            .withHeader("X-Physical-Tenant-Id", equalTo("tenantb")));
  }
}
