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
package io.camunda.connector.http.client.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.http.client.model.HttpClientResult;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ConnectorExceptionMapperTest {

  @Test
  public void shouldMapResultToException_whehOnlyStatusCode() {
    // given
    HttpClientResult result = new HttpClientResult(200, null, null, null, null);

    // when
    var exception = ConnectorExceptionMapper.from(result);

    // then
    assertThat(exception).isNotNull();
    assertThat(exception.getErrorCode()).isEqualTo("200");
    assertThat(exception.getMessage()).isEqualTo("[no reason]");
    var response = new HashMap<>();
    response.put("headers", null);
    response.put("body", null);
    assertThat(exception.getErrorVariables()).containsEntry("response", response);
  }

  @Test
  public void shouldMapResultToException_whenStatusCodeAndReason() {
    // given
    HttpClientResult result = new HttpClientResult(200, null, null, "Custom reason");

    // when
    var exception = ConnectorExceptionMapper.from(result);

    // then
    assertThat(exception).isNotNull();
    assertThat(exception.getErrorCode()).isEqualTo("200");
    assertThat(exception.getMessage()).isEqualTo("Custom reason");
    var response = new HashMap<>();
    response.put("headers", null);
    response.put("body", null);
    assertThat(exception.getErrorVariables()).containsEntry("response", response);
  }

  @Test
  public void shouldMapResultToException_whenStatusCodeAndHeaders() {
    // given
    HttpClientResult result =
        new HttpClientResult(
            200, Map.of("Content-Type", "text/plain", "X-Custom", "value"), null, null, null);

    // when
    var exception = ConnectorExceptionMapper.from(result);

    // then
    assertThat(exception).isNotNull();
    assertThat(exception.getErrorCode()).isEqualTo("200");
    assertThat(exception.getMessage()).isEqualTo("[no reason]");
    var response = new HashMap<>();
    response.put("headers", Map.of("Content-Type", "text/plain", "X-Custom", "value"));
    response.put("body", null);
    assertThat(exception.getErrorVariables()).containsEntry("response", response);
  }

  @Test
  public void shouldMapResultToException_whenStatusCodeAndBody() {
    // given
    HttpClientResult result = new HttpClientResult(400, null, "text", null, null);

    // when
    var exception = ConnectorExceptionMapper.from(result);

    // then
    assertThat(exception).isNotNull();
    assertThat(exception.getErrorCode()).isEqualTo("400");
    assertThat(exception.getMessage()).isEqualTo("[no reason]");
    var response = new HashMap<>();
    response.put("headers", null);
    response.put("body", "text");
    assertThat(exception.getErrorVariables()).containsEntry("response", response);
  }

  @Test
  public void shouldMapResultToException_whenStatusCodeAndBodyAndReason() {
    // given
    HttpClientResult result = new HttpClientResult(400, null, "text", "Custom reason");

    // when
    var exception = ConnectorExceptionMapper.from(result);

    // then
    assertThat(exception).isNotNull();
    assertThat(exception.getErrorCode()).isEqualTo("400");
    assertThat(exception.getMessage()).isEqualTo("Custom reason");
    var response = new HashMap<>();
    response.put("headers", null);
    response.put("body", "text");
    assertThat(exception.getErrorVariables()).containsEntry("response", response);
  }

  @Test
  public void shouldMapResultToException_whenStatusCodeAndBodyAndHeaders() {
    // given
    HttpClientResult result =
        new HttpClientResult(
            400, Map.of("Content-Type", "text/plain", "X-Custom", "value"), "text", null, null);

    // when
    var exception = ConnectorExceptionMapper.from(result);

    // then
    assertThat(exception).isNotNull();
    assertThat(exception.getErrorCode()).isEqualTo("400");
    assertThat(exception.getMessage()).isEqualTo("[no reason]");
    var response = new HashMap<>();
    response.put("headers", Map.of("Content-Type", "text/plain", "X-Custom", "value"));
    response.put("body", "text");
    assertThat(exception.getErrorVariables()).containsEntry("response", response);
  }

  @Test
  public void shouldMapResultToException_whenStatusCodeAndBodyAndHeadersAndReason() {
    // given
    HttpClientResult result =
        new HttpClientResult(
            400,
            Map.of("Content-Type", "text/plain", "X-Custom", "value"),
            "text",
            "Custom reason");

    // when
    var exception = ConnectorExceptionMapper.from(result);

    // then
    assertThat(exception).isNotNull();
    assertThat(exception.getErrorCode()).isEqualTo("400");
    assertThat(exception.getMessage()).isEqualTo("Custom reason");
    var response = new HashMap<>();
    response.put("headers", Map.of("Content-Type", "text/plain", "X-Custom", "value"));
    response.put("body", "text");
    assertThat(exception.getErrorVariables()).containsEntry("response", response);
  }

  @Test
  public void shouldMapResultToException_whenStatusCodeAndJsonBodyAndHeadersAndReason() {
    // given
    HttpClientResult result =
        new HttpClientResult(
            400,
            Map.of("Content-Type", "application/json", "X-Custom", "value"),
            Map.of("key", "value"),
            "Custom reason");

    // when
    var exception = ConnectorExceptionMapper.from(result);

    // then
    assertThat(exception).isNotNull();
    assertThat(exception.getErrorCode()).isEqualTo("400");
    assertThat(exception.getMessage()).isEqualTo("Custom reason");
    var response = new HashMap<>();
    response.put("headers", Map.of("Content-Type", "application/json", "X-Custom", "value"));
    response.put("body", Map.of("key", "value"));
    assertThat(exception.getErrorVariables()).containsEntry("response", response);
  }
}
