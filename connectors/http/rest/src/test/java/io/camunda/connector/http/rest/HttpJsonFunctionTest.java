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
package io.camunda.connector.http.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@WireMockTest(httpPort = 8086)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class HttpJsonFunctionTest extends BaseTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases.json";
  private static final String FAIL_CASES_RESOURCE_PATH =
      "src/test/resources/requests/fail-test-cases.json";

  private HttpJsonFunction functionUnderTest;

  private static Stream<String> successCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  private static Stream<String> failCases() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_CASES_RESOURCE_PATH);
  }

  @BeforeEach
  public void setup() {
    functionUnderTest = new HttpJsonFunction();
    stubFor(
        any(urlPathEqualTo("/http-endpoint"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json")));
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void shouldReturnResult_WhenExecuted(final String input) throws Exception {
    var functionCallResponseAsObject = arrange(input);

    assertThat(functionCallResponseAsObject.headers())
        .containsValue(APPLICATION_JSON.getMimeType());
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("failCases")
  void shouldReturnFallbackResult_WhenMalformedRequest(final String input) {
    final var context =
        OutboundConnectorContextBuilder.create()
            .variables(input)
            .validation(new DefaultValidationProvider())
            .secrets(name -> "foo")
            .build();

    // when
    var exceptionThrown = catchException(() -> functionUnderTest.execute(context));

    // then
    assertThat(exceptionThrown)
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("ValidationException");
  }

  @Test
  void execute_shouldReturnNullFieldWhenResponseWithContainNullField() throws Exception {
    // given request, and response body with null field value
    final var request =
        "{ \"method\": \"get\", \"url\": \"http://localhost:8086/http-endpoint\",\"authentication\": { \"type\": \"noAuth\" } }";
    final var response =
        "{ \"createdAt\": \"2022-10-10T05:03:14.723Z\", \"name\": \"Marvin Cremin\",\"unknown\": null, \"id\": \"1\" }";
    stubFor(any(urlPathEqualTo("/http-endpoint")).willReturn(aResponse().withBody(response)));

    final var context = OutboundConnectorContextBuilder.create().variables(request).build();
    // when connector execute
    var functionCallResponseAsObject = functionUnderTest.execute(context);
    // then null field 'unknown' exists in response body and has a null value
    var asJsonObject =
        objectMapper.convertValue(
            ((HttpCommonResult) functionCallResponseAsObject).body(), JsonNode.class);
    assertThat(asJsonObject.has("unknown")).isTrue();
    assertThat(asJsonObject.get("unknown").isNull()).isTrue();
  }

  private HttpCommonResult arrange(String input) throws Exception {
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();
    return (HttpCommonResult) functionUnderTest.execute(context);
  }
}
