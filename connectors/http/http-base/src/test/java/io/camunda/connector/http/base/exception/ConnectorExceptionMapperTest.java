/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.http.base.model.HttpCommonResult;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ConnectorExceptionMapperTest {

  @Test
  public void shouldMapResultToException_whehOnlyStatusCode() {
    // given
    HttpCommonResult result = new HttpCommonResult(200, null, null, null);

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
    HttpCommonResult result = new HttpCommonResult(200, null, null, "Custom reason");

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
    HttpCommonResult result =
        new HttpCommonResult(
            200, Map.of("Content-Type", "text/plain", "X-Custom", "value"), null, null);

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
    HttpCommonResult result = new HttpCommonResult(400, null, "text", null);

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
    HttpCommonResult result = new HttpCommonResult(400, null, "text", "Custom reason");

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
    HttpCommonResult result =
        new HttpCommonResult(
            400, Map.of("Content-Type", "text/plain", "X-Custom", "value"), "text", null);

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
    HttpCommonResult result =
        new HttpCommonResult(
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
    HttpCommonResult result =
        new HttpCommonResult(
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
