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
package io.camunda.connector.http.client.cloudfunction;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.HttpClientService;
import io.camunda.connector.http.client.TestDocumentFactory;
import io.camunda.connector.http.client.model.ErrorResponse;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpClientResult;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;

public class CloudFunctionResponseTransformer implements ResponseTransformerV2 {

  public static final String CLOUD_FUNCTION_TRANSFORMER = "cloud-function-transformer";
  private final CloudFunctionService cloudFunctionService = mock(CloudFunctionService.class);
  private final HttpClientService httpClientService = new HttpClientService(cloudFunctionService);

  public CloudFunctionResponseTransformer() {
    when(cloudFunctionService.isRunningInCloudFunction()).thenReturn(true);
  }

  @Override
  public Response transform(Response response, ServeEvent serveEvent) {
    String body = serveEvent.getRequest().getBodyAsString();
    try {
      HttpClientRequest request =
          ConnectorsObjectMapperSupplier.getCopy().readValue(body, HttpClientRequest.class);
      HttpClientResult value =
          httpClientService.executeConnectorRequest(request, new TestDocumentFactory());
      return Response.Builder.like(response)
          .but()
          .status(200)
          .body(
              ConnectorsObjectMapperSupplier.getCopy()
                  .writeValueAsString(
                      new HttpClientResult(
                          value.status(), value.headers(), value.body(), value.reason(), null)))
          .build();
    } catch (ConnectorException e) {
      try {
        return new Response.Builder()
            .status(500)
            .headers(new HttpHeaders(new HttpHeader("Content-Type", "application/json")))
            .body(
                ConnectorsObjectMapperSupplier.getCopy()
                    .writeValueAsString(
                        new ErrorResponse(e.getErrorCode(), e.getMessage(), e.getErrorVariables())))
            .build();
      } catch (JsonProcessingException ex) {
        throw new RuntimeException(ex);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String getName() {
    return CLOUD_FUNCTION_TRANSFORMER;
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }
}
