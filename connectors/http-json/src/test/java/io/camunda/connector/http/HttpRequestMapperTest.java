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

import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.apache.http.entity.ContentType.TEXT_PLAIN;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import io.camunda.connector.common.model.CommonRequest;
import io.camunda.connector.http.components.HttpTransportComponentSupplier;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpRequestMapperTest {

  private HttpRequestFactory httpRequestFactory;
  private CommonRequest request;

  @BeforeEach
  public void setUp() {
    httpRequestFactory = HttpTransportComponentSupplier.httpRequestFactoryInstance();
    request = new CommonRequest();
    request.setMethod("POST");
    request.setUrl("http://example.com");
    request.setBody("{ \"key\": \"value\" }");
  }

  @Test
  public void shouldSetJsonContentTypeWhenNotProvided() throws IOException {
    // given request without headers
    // when
    HttpRequest httpRequest = HttpRequestMapper.toHttpRequest(httpRequestFactory, request);
    // then
    HttpHeaders headers = httpRequest.getHeaders();
    assertThat(headers.getContentType()).isEqualTo(APPLICATION_JSON.getMimeType());
  }

  @Test
  public void shouldSetTextPlainContentTypeIfProvided() throws IOException {
    // given
    request.setHeaders(Map.of("Content-Type", "text/plain"));
    // when
    HttpRequest httpRequest = HttpRequestMapper.toHttpRequest(httpRequestFactory, request);
    // then
    HttpHeaders headers = httpRequest.getHeaders();
    assertThat(headers.getContentType()).isEqualTo(TEXT_PLAIN.getMimeType());
  }
}
