/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.appintegrations.model.AppIntegrationsConfiguration;
import io.camunda.connector.appintegrations.model.CreateChannelRequest;
import io.camunda.connector.appintegrations.model.auth.ApiKeyAuthentication;
import io.camunda.connector.appintegrations.model.auth.OAuthAuthentication;
import io.camunda.connector.http.client.authentication.OAuthConstants;
import io.camunda.connector.http.client.authentication.OAuthTokenCacheHolder;
import io.camunda.connector.http.client.authentication.cacheimpl.CaffeineOAuthTokenCache;
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
  private static final String TOKEN_PATH = "/oauth/token";

  private final AppIntegrationsConnector connector = new AppIntegrationsConnector();

  @BeforeEach
  @AfterEach
  void resetTokenCache() {
    // Start each test from a clean cache so the first OAuth fetch is always a miss, and don't leak
    // entries into other tests sharing the static holder.
    OAuthTokenCacheHolder.set(new CaffeineOAuthTokenCache());
  }

  private static CreateChannelRequest apiKeyChannelRequest(String baseUrl) {
    return new CreateChannelRequest(
        new AppIntegrationsConfiguration(baseUrl, new ApiKeyAuthentication("test-key")),
        "b7779302-e8cb-4b34-901b-5b150a19fd47",
        "My Channel",
        null,
        "standard");
  }

  private static CreateChannelRequest oauthChannelRequest(String baseUrl) {
    return new CreateChannelRequest(
        new AppIntegrationsConfiguration(
            baseUrl,
            new OAuthAuthentication(
                baseUrl + TOKEN_PATH,
                "client-id",
                "client-secret",
                "app-integrations",
                OAuthConstants.CREDENTIALS_BODY,
                null)),
        "b7779302-e8cb-4b34-901b-5b150a19fd47",
        "My Channel",
        null,
        "standard");
  }

  @Test
  void createChannel_realClient_success(WireMockRuntimeInfo wm) {
    stubFor(
        post(urlPathEqualTo(CHANNEL_PATH))
            .willReturn(okJson("{\"channelId\":\"19:new-channel@thread.tacv2\"}").withStatus(201)));

    var result = connector.createChannel(apiKeyChannelRequest(wm.getHttpBaseUrl()));

    assertThat(result.channelId()).isEqualTo("19:new-channel@thread.tacv2");
  }

  @Test
  void createChannel_realClient_serverError_throwsWithStatusCode(WireMockRuntimeInfo wm) {
    // Proves the crux of the design: the real SDK client THROWS on status >= 400 (it does not
    // return the response), with the status code as the connector exception's error code.
    stubFor(
        post(urlPathEqualTo(CHANNEL_PATH))
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

    assertThatThrownBy(() -> connector.createChannel(apiKeyChannelRequest(wm.getHttpBaseUrl())))
        .isInstanceOfSatisfying(
            ConnectorException.class, e -> assertThat(e.getErrorCode()).isEqualTo("500"));
  }

  @Test
  void createChannel_realClient_oauth401ThenSuccess_invalidatesAndRetries(WireMockRuntimeInfo wm) {
    stubFor(
        post(urlPathEqualTo(TOKEN_PATH))
            .willReturn(
                okJson(
                    "{\"access_token\":\"tok\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")));

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

    var result = connector.createChannel(oauthChannelRequest(wm.getHttpBaseUrl()));

    assertThat(result.channelId()).isEqualTo("19:after-retry@thread.tacv2");
    // The backend was called twice (the 401, then the successful retry) and the token was
    // re-fetched after invalidation (cache miss on the retry).
    verify(exactly(2), postRequestedFor(urlPathEqualTo(CHANNEL_PATH)));
    verify(exactly(2), postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
  }
}
