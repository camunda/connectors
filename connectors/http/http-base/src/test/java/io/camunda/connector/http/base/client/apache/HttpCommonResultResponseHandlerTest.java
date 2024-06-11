/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.client.apache;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.model.ErrorResponse;
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
    assertThat(result.status()).isEqualTo(200);
    assertThat((Map) result.body()).containsEntry("key", "value");
    assertThat(result.headers()).hasSize(1);
    assertThat(result.headers()).containsEntry("Content-Type", "application/json");
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
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isEqualTo("text");
    assertThat(result.headers()).hasSize(1);
    assertThat(result.headers()).containsEntry("Content-Type", "text/plain");
  }

  @Test
  public void shouldHandleJsonResponse_whenCloudFunctionEnabled() throws Exception {
    // given
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler(true);
    ClassicHttpResponse response = new BasicClassicHttpResponse(201);
    Header[] headers = new Header[] {new BasicHeader("Content-Type", "application/json")};
    response.setHeaders(headers);
    HttpCommonResult cloudFunctionResult =
        new HttpCommonResult(200, Map.of("X-Header", "value"), Map.of("key", "value"));
    response.setEntity(
        new StringEntity(
            ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(cloudFunctionResult)));

    // when
    HttpCommonResult result = handler.handleResponse(response);

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat((Map) result.body()).containsEntry("key", "value");
    assertThat(result.headers()).hasSize(1);
    assertThat(result.headers()).containsEntry("X-Header", "value");
  }

  @Test
  public void shouldHandleError_whenCloudFunctionEnabled() throws Exception {
    // given
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler(true);
    ClassicHttpResponse response = new BasicClassicHttpResponse(500);
    Header[] headers =
        new Header[] {
          new BasicHeader("Content-Type", "application/json"), new BasicHeader("X-Header", "value")
        };
    response.setHeaders(headers);
    response.setEntity(
        new StringEntity(
            ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(
                new ErrorResponse("500", "Custom message"))));

    // when
    HttpCommonResult result = handler.handleResponse(response);

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(500);
    assertThat((ErrorResponse) result.body()).isEqualTo(new ErrorResponse("500", "Custom message"));
    assertThat(result.headers()).hasSize(2);
    assertThat(result.headers()).containsEntry("X-Header", "value");
    assertThat(result.headers()).containsEntry("Content-Type", "application/json");
  }

  @Test
  public void shouldHandleJsonAsTextResponse_whenCloudFunctionEnabled() throws Exception {
    // given
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler(true);
    ClassicHttpResponse response = new BasicClassicHttpResponse(201);
    Header[] headers = new Header[] {new BasicHeader("Content-Type", "application/json")};
    response.setHeaders(headers);
    HttpCommonResult cloudFunctionResult =
        new HttpCommonResult(200, Map.of("X-Header", "value"), "{\"key\":\"value\"}");
    response.setEntity(
        new StringEntity(
            ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(cloudFunctionResult)));

    // when
    HttpCommonResult result = handler.handleResponse(response);

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isEqualTo("{\"key\":\"value\"}");
    assertThat(result.headers()).hasSize(1);
    assertThat(result.headers()).containsEntry("X-Header", "value");
  }
}
