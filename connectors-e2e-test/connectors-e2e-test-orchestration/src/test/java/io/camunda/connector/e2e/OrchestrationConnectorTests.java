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
package io.camunda.connector.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.process.test.api.CamundaAssert.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs the template through the connector runtime against a mocked Self-Managed Orchestration
 * Cluster API and token endpoint.
 */
@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@SlowTest
public class OrchestrationConnectorTests extends BaseOrchestrationTest {

  private static final String ACCESS_TOKEN = "test-access-token";

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private String tokenPath;

  @BeforeEach
  void stubTokenEndpoint() {
    // The runtime caches tokens per token endpoint, so each test uses its own endpoint to make sure
    // the token request is sent.
    tokenPath = "/oauth/token/" + UUID.randomUUID();
    wm.stubFor(
        post(urlPathEqualTo(tokenPath))
            .willReturn(
                ResponseDefinitionBuilder.okForJson(
                    Map.of("access_token", ACCESS_TOKEN, "expires_in", 3600))));
  }

  @Test
  void searchPostsTheBodyWithAnAccessToken() {
    var response =
        Map.of(
            "items", List.of(Map.of("processInstanceKey", KEY, "state", "ACTIVE")),
            "page", Map.of("totalItems", 1));
    wm.stubFor(
        post(urlPathEqualTo("/v2/process-instances/search"))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
            .withRequestBody(
                equalToJson("{\"filter\": {\"state\": \"ACTIVE\"}, \"page\": {\"limit\": 100}}"))
            .willReturn(ResponseDefinitionBuilder.okForJson(response)));

    var template =
        template("/process-instances", SEARCH)
            .propertyByBinding("input.internal_filter", "={state: \"ACTIVE\"}");
    var test = run(applyTemplate(template), Map.of()).waitForProcessCompletion();

    assertThat(test.getProcessInstanceEvent())
        .hasVariable("orchestrationClusterResponse", response);
    wm.verify(
        postRequestedFor(urlPathEqualTo(tokenPath))
            .withRequestBody(containing("grant_type=client_credentials"))
            .withRequestBody(containing("client_id=orchestration-client"))
            .withRequestBody(containing("client_secret=orchestration-secret"))
            .withRequestBody(containing("audience=orchestration-api")));
  }

  @Test
  void getByKeySendsAGetRequestWithTheKey() {
    var response = Map.of("incidentKey", KEY, "state", "ACTIVE");
    wm.stubFor(
        get(urlPathEqualTo("/v2/incidents/" + KEY))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
            .willReturn(ResponseDefinitionBuilder.okForJson(response)));

    var template =
        template("/incidents", GET_BY_KEY).propertyByBinding("input.internal_key", "=incidentKey");
    var test =
        run(applyTemplate(template), Map.of("incidentKey", 2251799813685249L))
            .waitForProcessCompletion();

    assertThat(test.getProcessInstanceEvent())
        .hasVariable("orchestrationClusterResponse", response);
  }

  @Test
  void unsetKeyRaisesAnIncidentWithoutCallingTheApi() {
    var template =
        template("/incidents", GET_BY_KEY)
            .propertyByBinding("input.internal_key", "=incidentKey")
            .property("retryCount", "1");

    run(applyTemplate(template), Map.of()).waitForActiveIncidents();

    wm.verify(0, anyRequestedFor(urlPathMatching("/v2/.*")));
  }

  private ElementTemplate template(String endpoint, String operation) {
    var baseUrl = "http://localhost:" + wm.getPort();
    return selfManagedTemplate(endpoint, operation, baseUrl + "/v2", baseUrl + tokenPath);
  }
}
