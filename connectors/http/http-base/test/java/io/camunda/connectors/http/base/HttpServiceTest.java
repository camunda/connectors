/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.http.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.client.client.apache.HttpCommonResultResponseHandler;
import java.util.Map;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

public class HttpServiceTest {

  @Test
  public void shouldHandleJsonResponse_whenCloudFunctionDisabled() throws Exception {
    // given
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler(null, false);
    ClassicHttpResponse response = new BasicClassicHttpResponse(200);
    Header[] headers = new Header[]{new BasicHeader("Content-Type", "application/json")};
    response.setHeaders(headers);
    response.setEntity(new StringEntity("{\"key\":\"value\"}"));

    // when
    HttpCommonResult result = new HttpService().mapToHttpCommonResult(
        handler.handleResponse(response));

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat((Map) result.body()).containsEntry("key", "value");
    assertThat(result.headers()).hasSize(1);
    assertThat(result.headers()).containsEntry("Content-Type", "application/json");
  }

  @Test
  public void shouldHandleTextResponse_whenCloudFunctionDisabled() throws Exception {
    // given
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler(null, false);
    ClassicHttpResponse response = new BasicClassicHttpResponse(200);
    Header[] headers = new Header[]{new BasicHeader("Content-Type", "text/plain")};
    response.setHeaders(headers);
    response.setEntity(new StringEntity("text"));

    // when
    HttpCommonResult result = new HttpService().mapToHttpCommonResult(
        handler.handleResponse(response));

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isEqualTo("text");
    assertThat(result.headers()).hasSize(1);
    assertThat(result.headers()).containsEntry("Content-Type", "text/plain");
  }

  @Test
  public void shouldReturnJSON_whenMapIsReturned() throws JsonProcessingException {
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler(null, false);
    ClassicHttpResponse response = new BasicClassicHttpResponse(200);
    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(Map.of("key1", "value1"));
    response.setEntity(new StringEntity(json));

    // when
    HttpCommonResult result = new HttpService().mapToHttpCommonResult(
        handler.handleResponse(response));

    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isEqualTo(Map.of("key1", "value1"));
  }

  @Test
  public void shouldReturnString_whenQuotes() {
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler(null, false);
    ClassicHttpResponse response = new BasicClassicHttpResponse(200);
    response.setEntity(new StringEntity("\"Hello, world\""));

    HttpCommonResult result = new HttpService().mapToHttpCommonResult(
        handler.handleResponse(response));

    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);

    assertEquals("\"Hello, world\"", result.body());
  }
}
