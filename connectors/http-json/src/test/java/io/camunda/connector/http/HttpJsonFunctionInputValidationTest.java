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
package io.camunda.connector.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.test.ConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpJsonFunctionInputValidationTest {

  private static final String REQUEST_METHOD_OBJECT_PLACEHOLDER =
      "{\n \"method\": \"%s\",\n \"url\": \"https://camunda.io/http-endpoint\"\n}";
  private static final String REQUEST_ENDPOINT_OBJECT_PLACEHOLDER =
      "{\n \"method\": \"get\",\n \"url\": \"%s\"\n}";

  private HttpJsonFunction functionUnderTest;

  @BeforeEach
  void setup() {
    functionUnderTest = new HttpJsonFunction();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\r\n"})
  void shouldRaiseException_WhenExecuted_MethodMalformed(final String input) {
    // Given
    ConnectorContext ctx =
        ConnectorContextBuilder.create()
            .variables(String.format(REQUEST_METHOD_OBJECT_PLACEHOLDER, input))
            .build();

    // When
    Throwable exception =
        assertThrows(IllegalArgumentException.class, () -> functionUnderTest.execute(ctx));

    // Then
    assertThat(exception.getMessage()).contains("HTTP Endpoint - Method");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "iAmWrongUrl", "ftp://camunda.org/", "camunda@camunda.com"})
  void shouldRaiseException_WhenExecuted_EndpointMalformed(final String input) {
    // Given
    ConnectorContext ctx =
        ConnectorContextBuilder.create()
            .variables(String.format(REQUEST_ENDPOINT_OBJECT_PLACEHOLDER, input))
            .build();

    // When
    Throwable exception =
        assertThrows(IllegalArgumentException.class, () -> functionUnderTest.execute(ctx));

    // Then
    assertThat(exception.getMessage()).contains("HTTP Endpoint - URL");
  }
}
