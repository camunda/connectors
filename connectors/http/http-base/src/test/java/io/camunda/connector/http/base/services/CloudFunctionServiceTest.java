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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.cloudfunction.CloudFunctionService;
import io.camunda.connector.http.base.model.ErrorResponse;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.model.auth.BearerAuthentication;
import java.io.IOException;
import java.util.Map;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CloudFunctionServiceTest {
  private static final CloudFunctionService cloudFunctionService = spy(new CloudFunctionService());

  @BeforeAll
  public static void setUp() {
    when(cloudFunctionService.getProxyFunctionUrl()).thenReturn("proxyUrl");
  }

  @Test
  public void shouldConvertToCloudFunctionRequest() throws IOException {
    // given
    HttpCommonRequest request = new HttpCommonRequest();
    request.setUrl("theUrl");
    request.setMethod(HttpMethod.POST);
    request.setHeaders(
        Map.of(
            "header",
            "value",
            "Content-Type",
            ContentType.APPLICATION_FORM_URLENCODED.getMimeType()));
    request.setBody(Map.of("bodyKey", "bodyValue"));
    request.setConnectionTimeoutInSeconds(50);
    request.setReadTimeoutInSeconds(60);
    request.setAuthentication(new BearerAuthentication("token"));

    // when
    HttpCommonRequest cloudFunctionRequest = cloudFunctionService.toCloudFunctionRequest(request);

    // then
    assertThat(cloudFunctionRequest.getUrl()).isEqualTo("proxyUrl");
    assertThat(cloudFunctionRequest.getMethod()).isEqualTo(HttpMethod.POST);
    assertThat(cloudFunctionRequest.getHeaders()).hasSize(1);
    assertThat(cloudFunctionRequest.getHeaders()).containsEntry("Content-Type", "application/json");
    Map<String, Object> body =
        ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readValue(
            (String) cloudFunctionRequest.getBody(), Map.class);
    assertThat(body).containsEntry("url", "theUrl");
    assertThat(body).containsEntry("method", "POST");
    assertThat(body)
        .containsEntry(
            "headers",
            Map.of("header", "value", "Content-Type", "application/x-www-form-urlencoded"));
    assertThat(body).containsEntry("body", Map.of("bodyKey", "bodyValue"));
    assertThat(body).containsEntry("connectionTimeoutInSeconds", 50);
    assertThat(body).containsEntry("readTimeoutInSeconds", 60);
    assertThat(body).containsEntry("authentication", Map.of("token", "token", "type", "bearer"));
  }

  @Test
  public void shouldUpdateError_whenExceptionMessageIsJson() throws JsonProcessingException {
    // given
    ConnectorException exception =
        new ConnectorException(
            "500",
            ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(
                Map.of("errorCode", "404", "error", "Not Found")));
    ErrorResponse errorResponse =
        new ErrorResponse(exception.getErrorCode(), exception.getMessage());

    // when
    errorResponse =
        cloudFunctionService.tryUpdateErrorUsingCloudFunctionError(exception, errorResponse);

    // then
    assertThat(errorResponse.error()).isEqualTo("Not Found");
    assertThat(errorResponse.errorCode()).isEqualTo("404");
  }

  @Test
  public void shouldNotUpdateError_whenExceptionMessageIsNotJson() throws JsonProcessingException {
    // given
    ConnectorException exception = new ConnectorException("500", "Unknown error");
    ErrorResponse errorResponse =
        new ErrorResponse(exception.getErrorCode(), exception.getMessage());

    // when
    errorResponse =
        cloudFunctionService.tryUpdateErrorUsingCloudFunctionError(exception, errorResponse);

    // then
    assertThat(errorResponse.error()).isEqualTo("Unknown error");
    assertThat(errorResponse.errorCode()).isEqualTo("500");
  }
}
