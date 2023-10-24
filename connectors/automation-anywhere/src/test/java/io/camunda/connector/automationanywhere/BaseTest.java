/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public abstract class BaseTest {

  protected static Object authenticationResponse;
  protected static final int STATUS_SUCCESS = 200;
  protected static final String AUTH_URL = "https://automation-anywhere-mock.com/v1/authentication";
  protected static final Object EXPECTED_BODY_WITH_FILTER =
      Map.of("filter", Map.of("field", "id", "operator", "eq", "value", 31250));
  protected static final Object EXPECTED_BODY_WITH_ITEM =
      Map.of(
          "workItems",
          List.of(
              Map.of(
                  "json",
                  Map.of(
                      "coll_name",
                      "your value",
                      "email",
                      "jane.doe@example.com",
                      "last_name",
                      "Doe"))));
  protected static final Object EXPECTED_API_KEY_BODY =
      Map.of("apiKey", "myApiKey", "username", "Jane");
  protected static final Object EXPECTED_PASSWORD_BODY =
      Map.of("multipleLogin", false, "password", "myPassword", "username", "Jane");
  protected static final String EXPECTED_GET_ITEM_URL =
      "https://automation-anywhere-mock.com/v3/wlm/queues/workQueueId/workitems/list";
  protected static final String EXPECTED_CREATE_ITEM_URL =
      "https://automation-anywhere-mock.com/v3/wlm/queues/workQueueId/workitems";
  protected static final int TIMEOUT = 20;
  protected static ObjectMapper objectMapper;

  protected static final String SUCCESS_TEST_CASES_PASSWORD_BASED_AUTH_WITH_SECRETS =
      "src/test/resources/requests/success-test-cases-password-based-auth-with-secrets.json";

  protected static final String SUCCESS_TEST_CASES_API_KEY_AUTH_WITH_SECRETS =
      "src/test/resources/requests/success-test-cases-api-key-auth-with-secrets.json";

  protected static final String SUCCESS_TEST_CASES_TOKEN_BASED_AUTH_WITH_SECRETS =
      "src/test/resources/requests/success-test-cases-token-based-auth-with-secrets.json";

  protected static final String SUCCESS_AUTHENTICATION_RESPONSE_PATH =
      "src/test/resources/response/success-authentication-response.json";

  protected static Stream<String> successPasswordBasedWithSecretsCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_TEST_CASES_PASSWORD_BASED_AUTH_WITH_SECRETS);
  }

  protected static Stream<String> successApiKeyAuthWithSecretsCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_TEST_CASES_API_KEY_AUTH_WITH_SECRETS);
  }

  protected static Stream<String> successTokenBasedAuthWithSecretsCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_TEST_CASES_TOKEN_BASED_AUTH_WITH_SECRETS);
  }

  protected OutboundConnectorContextBuilder getContextWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret("USERNAME", "Jane")
        .secret("PASSWORD", "myPassword")
        .secret("API_KEY", "myApiKey")
        .secret("TOKEN", "thisIsTestToken.0123344567890qwertyuiopASDFGHJKLxcvbnm-Ug")
        .secret("WORK_ITEM_ID", "31250")
        .secret("EMAIL", "jane.doe@example.com");
  }

  @SuppressWarnings("unchecked")
  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    final String cases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    final ObjectMapper mapper = ConnectorsObjectMapperSupplier.getCopy();
    var array = mapper.readValue(cases, ArrayList.class);
    return array.stream()
        .map(
            value -> {
              try {
                return mapper.writeValueAsString(value);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            });
  }
}
