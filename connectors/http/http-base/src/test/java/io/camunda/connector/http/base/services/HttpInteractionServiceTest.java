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
package io.camunda.connector.http.base.services;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.apache.http.entity.ContentType.APPLICATION_XML;
import static org.apache.http.entity.ContentType.TEXT_PLAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponse;
import com.google.gson.Gson;
import io.camunda.connector.http.base.model.HttpCommonResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpInteractionServiceTest {

  @Mock private HttpResponse httpResponse;
  private HttpInteractionService httpInteractionService;

  private static final String JSON_BODY =
      "{\"name\":\"John\",\"weight\":60.6,\"city\":\"New York\"}";
  private static final String XML_BODY =
      "<person><name>John</name><weight>60.6</weight><city>New York</city></person>";
  private static final String TEXT_BODY = "Hello, World!";

  @BeforeEach
  public void setUp() {
    httpInteractionService = new HttpInteractionService(new Gson());
  }

  @Test
  public void toHttpResponse_withJsonBody_shouldReturnJsonContentTypeWithJsonBody()
      throws IOException, InstantiationException, IllegalAccessException {
    // Given
    when(httpResponse.getStatusCode()).thenReturn(200);
    when(httpResponse.getContent())
        .thenReturn(new ByteArrayInputStream(JSON_BODY.getBytes(StandardCharsets.UTF_8)));
    when(httpResponse.getHeaders())
        .thenReturn(new HttpHeaders().set("content-type", APPLICATION_JSON.getMimeType()));
    // When
    HttpCommonResult httpCommonResult =
        httpInteractionService.toHttpResponse(httpResponse, HttpCommonResult.class);
    // Then
    assertThat(httpCommonResult).isNotNull();
    assertEquals(200, httpCommonResult.getStatus());

    assertThat(httpCommonResult.getHeaders().get("content-type"))
        .isEqualTo(APPLICATION_JSON.getMimeType());

    assertThat(httpCommonResult.getBody())
        .hasFieldOrPropertyWithValue("name", "John")
        .hasFieldOrPropertyWithValue("weight", 60.6)
        .hasFieldOrPropertyWithValue("city", "New York");
  }

  @Test
  public void toHttpResponse_withXMLBody_shouldReturnXMLContentTypeWithTextBody() throws Exception {
    // Given
    when(httpResponse.getStatusCode()).thenReturn(200);
    when(httpResponse.getContent())
        .thenReturn(new ByteArrayInputStream(XML_BODY.getBytes(StandardCharsets.UTF_8)));
    when(httpResponse.getHeaders())
        .thenReturn(new HttpHeaders().set("content-type", APPLICATION_XML.getMimeType()));
    // When
    HttpCommonResult httpCommonResult =
        httpInteractionService.toHttpResponse(httpResponse, HttpCommonResult.class);
    // Then
    assertThat(httpCommonResult).isNotNull();
    assertEquals(200, httpCommonResult.getStatus());

    assertThat(httpCommonResult.getHeaders().get("content-type"))
        .isEqualTo(APPLICATION_XML.getMimeType());

    assertThat(httpCommonResult.getBody()).isEqualTo(XML_BODY);
  }

  @Test
  public void toHttpResponse_withTextBody_shouldReturnTextPlainContentTypeWithTextBody()
      throws Exception {
    // Given
    when(httpResponse.getStatusCode()).thenReturn(200);
    when(httpResponse.getContent())
        .thenReturn(new ByteArrayInputStream(TEXT_BODY.getBytes(StandardCharsets.UTF_8)));
    when(httpResponse.getHeaders())
        .thenReturn(new HttpHeaders().set("content-type", TEXT_PLAIN.getMimeType()));
    // When
    HttpCommonResult httpCommonResult =
        httpInteractionService.toHttpResponse(httpResponse, HttpCommonResult.class);
    // Then
    assertThat(httpCommonResult).isNotNull();
    assertEquals(200, httpCommonResult.getStatus());
    assertThat(httpCommonResult.getHeaders().get("content-type"))
        .isEqualTo(TEXT_PLAIN.getMimeType());

    assertThat(httpCommonResult.getBody()).isEqualTo(TEXT_BODY);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"name\":\"John\", \"age\":30}", "[1, 2, 3]"})
  public void ssJSONValid_shouldReturnTrueIfJSONIsValid(String input) {
    boolean result = HttpInteractionService.isJSONValid(input);
    assertTrue(result);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{name:\"John\", city:New York}", "Invalid JSON string"})
  public void ssJSONValid_shouldReturnFalseForInvalidJSON(String input) {
    boolean result = HttpInteractionService.isJSONValid(input);
    assertThat(result).isFalse();
  }
}
