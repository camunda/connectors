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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.http.base.model.auth.OAuthAuthentication;
import io.camunda.connector.http.rest.model.HttpJsonRequest;
import io.camunda.connector.http.rest.templates.TemplateInputMappings.Mode;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The UiPath template is a hand-maintained template on top of the HTTP JSON connector. Each case
 * runs in both of Zeebe's input mapping modes.
 */
class UiPathElementTemplateTest {

  private static final String TEMPLATE = "../../uipath/element-templates/uipath-connector.json";

  private static final String AUTOMATION_SUITE_HOST = "uipath.automation-suite.example.test";
  private static final String OTHER_HOST = "uipath.process-variable.example.test";

  // The default validation resolves the URL's host; these tests only read the bound request.
  private static final ValidationProvider NO_HOST_LOOKUP = input -> {};

  static Stream<Arguments> operationsAndModes() {
    return Stream.of("addQueueItem", "getItemById")
        .flatMap(
            operation -> Arrays.stream(Mode.values()).map(mode -> Arguments.of(operation, mode)));
  }

  /** What the user enters, keyed by property id where the property has one. */
  private static Map<String, String> oauthInputs(String operation) {
    Map<String, String> inputs = new HashMap<>();
    inputs.put("operationType", operation);
    inputs.put("authenticationType", "oauth-client-credentials-flow");
    inputs.put("authentication.clientId", "synthetic-client-9999");
    inputs.put("authentication.clientSecret", "synthetic-secret-9999");
    inputs.put("cloudOrg", "fictional-org");
    inputs.put("cloudTenant", "DefaultTenant");
    inputs.put("organizationUnitId", "9999");
    inputs.put("queueName", "synthetic-invoices");
    inputs.put("itemId", "=436141352");
    return inputs;
  }

  private static Map<String, String> oauthInputs(String operation, String instanceHost) {
    Map<String, String> inputs = oauthInputs(operation);
    inputs.put("cloudUrl", instanceHost);
    return inputs;
  }

  private static HttpJsonRequest bind(Map<String, Object> variables) throws Exception {
    return OutboundConnectorContextBuilder.create()
        .variables(variables)
        .validation(NO_HOST_LOOKUP)
        .build()
        .bindVariables(HttpJsonRequest.class);
  }

  private static String tokenEndpoint(String host) {
    return "https://" + host + "/identity_/connect/token";
  }

  private static void assertTokenEndpoint(HttpJsonRequest request, String host) {
    assertThat(request.getAuthentication())
        .isInstanceOfSatisfying(
            OAuthAuthentication.class,
            auth -> assertThat(auth.oauthTokenEndpoint()).isEqualTo(tokenEndpoint(host)));
  }

  // "UiPath instance URL" defaults to cloud.uipath.com and can be changed (Automation Suite,
  // other UiPath clouds). The client credentials must then go to that host, not to
  // cloud.uipath.com. These cases pin the host only: UiPath's Automation Suite guides also put the
  // organization in the token path, which the template does not do.
  @ParameterizedTest(name = "{0}, {1}")
  @MethodSource("operationsAndModes")
  void oauthTokenIsRequestedFromTheConfiguredInstance(String operation, Mode mode)
      throws Exception {
    var variables =
        TemplateInputMappings.load(TEMPLATE, mode)
            .apply(oauthInputs(operation, AUTOMATION_SUITE_HOST));

    var request = bind(variables);

    assertThat(request.getUrl())
        .startsWith("https://" + AUTOMATION_SUITE_HOST + "/fictional-org/DefaultTenant/");
    assertTokenEndpoint(request, AUTOMATION_SUITE_HOST);
  }

  @ParameterizedTest(name = "{0}, {1}")
  @MethodSource("operationsAndModes")
  void processVariableCloudUrlDoesNotChangeTheTokenEndpoint(String operation, Mode mode)
      throws Exception {
    var variables =
        TemplateInputMappings.load(TEMPLATE, mode)
            .apply(oauthInputs(operation, AUTOMATION_SUITE_HOST), Map.of("cloudUrl", OTHER_HOST));

    var request = bind(variables);

    assertThat(request.getUrl()).startsWith("https://" + AUTOMATION_SUITE_HOST + "/");
    assertTokenEndpoint(request, AUTOMATION_SUITE_HOST);
  }

  @ParameterizedTest(name = "{0}, {1}")
  @MethodSource("operationsAndModes")
  void defaultInstanceKeepsTheAutomationCloudTokenEndpoint(String operation, Mode mode)
      throws Exception {
    var variables = TemplateInputMappings.load(TEMPLATE, mode).apply(oauthInputs(operation));

    var request = bind(variables);

    assertThat(request.getUrl()).startsWith("https://cloud.uipath.com/fictional-org/");
    assertTokenEndpoint(request, "cloud.uipath.com");
  }

  @ParameterizedTest(name = "{0}, {1}")
  @MethodSource("operationsAndModes")
  void defaultInstanceIgnoresAProcessVariableCloudUrl(String operation, Mode mode)
      throws Exception {
    var variables =
        TemplateInputMappings.load(TEMPLATE, mode)
            .apply(oauthInputs(operation), Map.of("cloudUrl", OTHER_HOST));

    var request = bind(variables);

    assertThat(request.getUrl()).startsWith("https://cloud.uipath.com/fictional-org/");
    assertTokenEndpoint(request, "cloud.uipath.com");
  }

  private static final String CREDENTIAL_TYPE = "io.camunda.connectors:uipath-authentication:1";

  /**
   * The value saved for a credential of the template's own UiPath credential type: the entered
   * values, and the hidden constants of the properties whose condition holds, at their binding
   * paths.
   *
   * @param entered values the admin enters, keyed by property id
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> credential(Map<String, String> entered) throws Exception {
    JsonNode template = new ObjectMapper().readTree(new File(TEMPLATE));
    JsonNode type =
        StreamSupport.stream(template.get("configurationTemplates").spliterator(), false)
            .filter(t -> CREDENTIAL_TYPE.equals(t.path("id").asText()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no credential type " + CREDENTIAL_TYPE));
    Map<String, String> values = new HashMap<>();
    for (JsonNode property : type.get("properties")) {
      String id = property.path("id").asText();
      String value =
          entered.containsKey(id) ? entered.get(id) : property.path("value").asText(null);
      if (value != null) {
        values.put(id, value);
      }
    }
    Map<String, Object> credential = new LinkedHashMap<>();
    for (JsonNode property : type.get("properties")) {
      JsonNode condition = property.path("condition");
      if (!condition.isMissingNode()) {
        if (!condition.has("equals")) {
          throw new IllegalArgumentException("Unsupported condition: " + condition);
        }
        if (!condition
            .get("equals")
            .asText()
            .equals(values.get(condition.path("property").asText()))) {
          continue;
        }
      }
      String value = values.get(property.path("id").asText());
      if (value == null || value.isEmpty()) {
        continue;
      }
      String[] path = property.path("binding").path("name").asText().split("\\.");
      Map<String, Object> current = credential;
      for (int i = 0; i < path.length - 1; i++) {
        current =
            (Map<String, Object>)
                current.computeIfAbsent(path[i], key -> new LinkedHashMap<String, Object>());
      }
      current.put(path[path.length - 1], value);
    }
    return credential;
  }

  /** What the admin enters for an OAuth credential; a null host keeps the default Cloud URL. */
  private static Map<String, String> oauthCredentialEntries(String instanceHost) {
    Map<String, String> entries = new HashMap<>();
    entries.put("authenticationType", "oauth-client-credentials-flow");
    entries.put("authentication.clientId", "synthetic-client-9999");
    entries.put("authentication.clientSecret", "synthetic-secret-9999");
    if (instanceHost != null) {
      entries.put("cloudUrl", instanceHost);
    }
    entries.put("cloudOrg", "fictional-org");
    entries.put("cloudTenant", "DefaultTenant");
    return entries;
  }

  /** What the user enters on the task when a reusable credential is chosen. */
  private static Map<String, String> credentialTaskInputs(String operation) {
    Map<String, String> inputs = new HashMap<>();
    inputs.put("operationType", operation);
    inputs.put("serviceAuthenticationConfiguration", "=uiPathCredential");
    inputs.put("organizationUnitId", "9999");
    inputs.put("queueName", "synthetic-invoices");
    inputs.put("itemId", "=436141352");
    return inputs;
  }

  // A reusable UiPath credential holds the instance host as its Cloud URL parameter, and its base
  // URL is built from it. The OAuth token has to come from the same host.
  @ParameterizedTest(name = "{0}, {1}")
  @MethodSource("operationsAndModes")
  void credentialRequestsTheOAuthTokenFromItsInstance(String operation, Mode mode)
      throws Exception {
    var credential = credential(oauthCredentialEntries(AUTOMATION_SUITE_HOST));
    var variables =
        TemplateInputMappings.load(TEMPLATE, mode)
            .apply(credentialTaskInputs(operation), Map.of("uiPathCredential", credential));

    var request = bind(variables);

    assertThat(request.getUrl())
        .startsWith("https://" + AUTOMATION_SUITE_HOST + "/fictional-org/DefaultTenant/");
    assertTokenEndpoint(request, AUTOMATION_SUITE_HOST);
  }

  @ParameterizedTest(name = "{0}, {1}")
  @MethodSource("operationsAndModes")
  void credentialWithTheDefaultInstanceKeepsTheAutomationCloudTokenEndpoint(
      String operation, Mode mode) throws Exception {
    var credential = credential(oauthCredentialEntries(null));
    var variables =
        TemplateInputMappings.load(TEMPLATE, mode)
            .apply(credentialTaskInputs(operation), Map.of("uiPathCredential", credential));

    var request = bind(variables);

    assertThat(request.getUrl()).startsWith("https://cloud.uipath.com/fictional-org/");
    assertTokenEndpoint(request, "cloud.uipath.com");
  }
}
