/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.components.apache;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.model.HttpCommonResult;
import java.util.Map;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

public class HttpCommonResultResponseHandlerTest {

  @Test
  public void shouldHandleJsonResponse_whenCloudFunctionDisabled() throws Exception {
    // given
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler(false);
    ClassicHttpResponse response = new BasicClassicHttpResponse(200);
    Header[] headers = new Header[] {new BasicHeader("Content-Type", "application/json")};
    response.setHeaders(headers);
    response.setEntity(new StringEntity("{\"key\":\"value\"}"));

    // when
    HttpCommonResult result = handler.handleResponse(response);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
    assertThat((Map) result.getBody()).containsEntry("key", "value");
    assertThat(result.getHeaders()).hasSize(1);
    assertThat(result.getHeaders()).containsEntry("Content-Type", "application/json");
  }

  @Test
  public void shouldHandleTextResponse_whenCloudFunctionDisabled() throws Exception {
    // given
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler(false);
    ClassicHttpResponse response = new BasicClassicHttpResponse(200);
    Header[] headers = new Header[] {new BasicHeader("Content-Type", "text/plain")};
    response.setHeaders(headers);
    response.setEntity(new StringEntity("text"));

    // when
    HttpCommonResult result = handler.handleResponse(response);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
    assertThat(result.getBody()).isEqualTo("text");
    assertThat(result.getHeaders()).hasSize(1);
    assertThat(result.getHeaders()).containsEntry("Content-Type", "text/plain");
  }

  @Test
  public void shouldHandleJsonResponse_whenCloudFunctionEnabled() throws Exception {
    // given
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler(true);
    ClassicHttpResponse response = new BasicClassicHttpResponse(201);
    Header[] headers = new Header[] {new BasicHeader("Content-Type", "application/json")};
    response.setHeaders(headers);
    HttpCommonResult cloudFunctionResult = new HttpCommonResult();
    cloudFunctionResult.setBody(Map.of("key", "value"));
    cloudFunctionResult.setHeaders(Map.of("X-Header", "value"));
    cloudFunctionResult.setStatus(200);
    response.setEntity(
        new StringEntity(
            ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(cloudFunctionResult)));

    // when
    HttpCommonResult result = handler.handleResponse(response);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
    assertThat((Map) result.getBody()).containsEntry("key", "value");
    assertThat(result.getHeaders()).hasSize(1);
    assertThat(result.getHeaders()).containsEntry("X-Header", "value");
  }

  @Test
  public void shouldHandleJsonAsTextResponse_whenCloudFunctionEnabled() throws Exception {
    // given
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler(true);
    ClassicHttpResponse response = new BasicClassicHttpResponse(201);
    Header[] headers = new Header[] {new BasicHeader("Content-Type", "application/json")};
    response.setHeaders(headers);
    HttpCommonResult cloudFunctionResult = new HttpCommonResult();
    cloudFunctionResult.setBody("{\"key\":\"value\"}");
    cloudFunctionResult.setHeaders(Map.of("X-Header", "value"));
    cloudFunctionResult.setStatus(200);
    response.setEntity(
        new StringEntity(
            ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(cloudFunctionResult)));

    // when
    HttpCommonResult result = handler.handleResponse(response);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
    assertThat(result.getBody()).isEqualTo("{\"key\":\"value\"}");
    assertThat(result.getHeaders()).hasSize(1);
    assertThat(result.getHeaders()).containsEntry("X-Header", "value");
  }
}
