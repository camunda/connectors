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

import io.camunda.connector.http.client.mapper.StreamingHttpResponse;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ConnectorExceptionMapperTest {

  @Test
  public void shouldMapResultToException_whenOnlyStatusCode() {
    // given
    StreamingHttpResponse result = new StreamingHttpResponse(200, null, null, null);

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
    StreamingHttpResponse result = new StreamingHttpResponse(200, "Custom reason", null, null);

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
    StreamingHttpResponse result =
        new StreamingHttpResponse(
            200,
            null,
            Map.of("Content-Type", List.of("text/plain"), "X-Custom", List.of("value")),
            null);

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
    StreamingHttpResponse result =
        new StreamingHttpResponse(400, null, null, new ByteArrayInputStream("text".getBytes()));

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
    StreamingHttpResponse result =
        new StreamingHttpResponse(
            400, "Custom reason", null, new ByteArrayInputStream("text".getBytes()));

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
    StreamingHttpResponse result =
        new StreamingHttpResponse(
            400,
            null,
            Map.of("Content-Type", List.of("text/plain"), "X-Custom", List.of("value")),
            new ByteArrayInputStream("text".getBytes()));

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
    StreamingHttpResponse result =
        new StreamingHttpResponse(
            400,
            "Custom reason",
            Map.of("Content-Type", List.of("text/plain"), "X-Custom", List.of("value")),
            new ByteArrayInputStream("text".getBytes()));

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
    StreamingHttpResponse result =
        new StreamingHttpResponse(
            400,
            "Custom reason",
            Map.of("Content-Type", List.of("application/json"), "X-Custom", List.of("value")),
            new ByteArrayInputStream("{\"key\":\"value\"}".getBytes()));

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
