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
package io.camunda.connector.http.rest.templates;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.feel.LocalFeelExpressionEvaluator;
import io.camunda.connector.http.base.model.auth.OAuthAuthentication;
import io.camunda.connector.http.base.model.auth.ServiceAuthenticationConfiguration;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The UiPath template is a hand-maintained template on top of the HTTP JSON connector. "UiPath
 * instance URL" defaults to cloud.uipath.com and can be changed (Automation Suite, other UiPath
 * clouds). The OAuth client credentials must then go to that host, not to cloud.uipath.com.
 */
class UiPathElementTemplateTest {

  private static final LocalFeelExpressionEvaluator FEEL = new LocalFeelExpressionEvaluator();

  private static JsonNode template() throws Exception {
    return ConnectorsObjectMapperSupplier.getCopy()
        .readTree(new File("../../uipath/element-templates/uipath-connector.json"));
  }

  private static JsonNode credentialType() throws Exception {
    for (JsonNode type : template().path("configurationTemplates")) {
      if (type.path("id").asText().equals("io.camunda.connectors:uipath-authentication:1")) {
        return type;
      }
    }
    throw new AssertionError("Missing the UiPath credential type");
  }

  private static JsonNode property(JsonNode properties, String bindingName) {
    for (JsonNode property : properties) {
      if (property.path("binding").path("name").asText().equals(bindingName)) {
        return property;
      }
    }
    throw new AssertionError("Missing property bound to " + bindingName);
  }

  @Test
  void instanceUrlDefaultsToAutomationCloud() throws Exception {
    assertThat(property(template().path("properties"), "cloudUrl").path("value").asText())
        .isEqualTo("cloud.uipath.com");
    assertThat(
            property(credentialType().path("properties"), "parameters.cloudUrl")
                .path("value")
                .asText())
        .isEqualTo("cloud.uipath.com");
  }

  // On the task, the token endpoint is a hidden FEEL expression over the inputs of the template.
  @ParameterizedTest
  @CsvSource({
    "cloud.uipath.com, https://cloud.uipath.com/identity_/connect/token",
    "uipath.automation-suite.example.test,"
        + " https://uipath.automation-suite.example.test/identity_/connect/token"
  })
  void taskRequestsTheOAuthTokenFromTheConfiguredInstance(String instanceUrl, String tokenEndpoint)
      throws Exception {
    String expression =
        property(template().path("properties"), "authentication.oauthTokenEndpoint")
            .path("value")
            .asText();

    String result =
        FEEL.evaluate(expression, Map.of("cloudUrl", instanceUrl, "cloudOrg", "fictional-org"));

    assertThat(result).isEqualTo(tokenEndpoint);
  }

  // In the reusable credential, the token endpoint and the base URL are constants with ${...}
  // placeholders, which the connector fills from the parameters of the credential.
  @ParameterizedTest
  @CsvSource({
    "cloud.uipath.com, https://cloud.uipath.com/identity_/connect/token",
    "uipath.automation-suite.example.test,"
        + " https://uipath.automation-suite.example.test/identity_/connect/token"
  })
  void credentialRequestsTheOAuthTokenFromItsInstance(String instanceUrl, String tokenEndpoint)
      throws Exception {
    JsonNode properties = credentialType().path("properties");
    Map<String, String> entered =
        Map.of(
            "cloudUrl", instanceUrl, "cloudOrg", "fictional-org", "cloudTenant", "DefaultTenant");
    Map<String, String> parameters = new HashMap<>();
    for (JsonNode property : properties) {
      String name = property.path("binding").path("name").asText();
      if (name.startsWith("parameters.")) {
        parameters.put(
            name.substring("parameters.".length()), entered.get(property.path("id").asText()));
      }
    }

    var credential =
        new ServiceAuthenticationConfiguration(
            new OAuthAuthentication(
                property(properties, "authentication.oauthTokenEndpoint").path("value").asText(),
                "synthetic-client-9999",
                "synthetic-secret-9999",
                null,
                "basicAuthHeader",
                "OR.Queues"),
            property(properties, "baseUrl").path("value").asText(),
            parameters);

    assertThat(credential.baseUrl())
        .isEqualTo("https://" + instanceUrl + "/fictional-org/DefaultTenant");
    assertThat(credential.authentication())
        .isInstanceOfSatisfying(
            OAuthAuthentication.class,
            oauth -> assertThat(oauth.oauthTokenEndpoint()).isEqualTo(tokenEndpoint));
  }
}
