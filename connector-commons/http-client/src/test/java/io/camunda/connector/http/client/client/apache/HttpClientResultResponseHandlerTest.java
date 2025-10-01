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
package io.camunda.connector.http.client.client.apache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.connector.http.client.model.HttpClientResult;
import io.camunda.connector.http.client.model.ResponseBody;
import java.io.IOException;
import java.io.InputStream;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

public class HttpClientResultResponseHandlerTest {

  private final String RESPONSE_BODY = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";

  @Test
  public void shouldProduceInputStream() throws IOException {
    // given
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler();
    ClassicHttpResponse response = new BasicClassicHttpResponse(200);
    StringEntity entity = new StringEntity(RESPONSE_BODY);
    response.setEntity(entity);

    // when
    HttpClientResult result = handler.handleResponse(response);

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    var body = result.body();
    assertThat(body).isNotNull();
    InputStream inputStream = body.getStream();
    byte[] bytes = inputStream.readAllBytes();
    String readString = new String(bytes);
    assertThat(readString).isEqualTo(RESPONSE_BODY);
  }

  @Test
  public void shouldProduceBytes() throws IOException {
    // given
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler();
    ClassicHttpResponse response = new BasicClassicHttpResponse(200);
    StringEntity entity = new StringEntity(RESPONSE_BODY);
    response.setEntity(entity);

    // when
    HttpClientResult result = handler.handleResponse(response);

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    var body = result.body();
    assertThat(body).isNotNull();
    byte[] bytes = body.readBytes();
    String readString = new String(bytes);
    assertThat(readString).isEqualTo(RESPONSE_BODY);

    // the body can be read multiple times
    byte[] bytes2 = body.readBytes();
    String readString2 = new String(bytes2);
    assertThat(readString2).isEqualTo(RESPONSE_BODY);
  }

  @Test
  public void shouldPreserveHeaders() {
    // given
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler();
    ClassicHttpResponse response = new BasicClassicHttpResponse(200);
    Header[] headers =
        new Header[] {
          new BasicHeader("Content-Type", "application/json"),
          new BasicHeader("X-Custom-Header", "custom-value")
        };
    response.setHeaders(headers);
    StringEntity entity = new StringEntity(RESPONSE_BODY);
    response.setEntity(entity);

    // when
    HttpClientResult result = handler.handleResponse(response);

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.headers()).hasSize(2);
    assertThat(result.headers()).containsEntry("Content-Type", "application/json");
    assertThat(result.headers()).containsEntry("X-Custom-Header", "custom-value");
  }

  @Test
  public void shouldHandleErrorResponse() throws IOException {
    // given
    HttpCommonResultResponseHandler handler = new HttpCommonResultResponseHandler();
    ClassicHttpResponse response = new BasicClassicHttpResponse(500);
    StringEntity entity = new StringEntity("Internal Server Error: something went wrong");
    response.setEntity(entity);

    // when
    HttpClientResult result = handler.handleResponse(response);

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(500);
    var body = result.body();
    assertThat(body).isNotNull();
    byte[] bytes = body.readBytes();
    String readString = new String(bytes);
    assertThat(readString).isEqualTo("Internal Server Error: something went wrong");
    assertThat(result.reason()).isEqualTo("Internal Server Error");
  }

  @Test
  public void closingResponseObjectShouldCloseBodyStream() throws IOException {
    // given
    var mockStream = mock(InputStream.class);

    // when
    var body = new ResponseBody(mockStream);
    HttpClientResult result = new HttpClientResult(200, null, body, "OK");
    result.close();

    // then
    // verify that the body stream was closed when closing the response
    verify(mockStream, times(1)).close();
  }
}
