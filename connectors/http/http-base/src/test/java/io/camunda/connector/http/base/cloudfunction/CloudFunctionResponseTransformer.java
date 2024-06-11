/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.cloudfunction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.base.model.ErrorResponse;
import io.camunda.connector.http.base.model.HttpCommonRequest;

public class CloudFunctionResponseTransformer implements ResponseTransformerV2 {

  public static final String CLOUD_FUNCTION_TRANSFORMER = "cloud-function-transformer";
  private final HttpService httpService = new HttpService();

  public CloudFunctionResponseTransformer() {}

  @Override
  public Response transform(Response response, ServeEvent serveEvent) {
    String body = serveEvent.getRequest().getBodyAsString();
    try {
      HttpCommonRequest request =
          ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readValue(body, HttpCommonRequest.class);
      return Response.Builder.like(response)
          .but()
          .status(200)
          .body(
              ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(
                  httpService.executeConnectorRequest(request)))
          .build();
    } catch (ConnectorException e) {
      try {
        return new Response.Builder()
            .status(500)
            .headers(new HttpHeaders(new HttpHeader("Content-Type", "application/json")))
            .body(
                ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(
                    new ErrorResponse(e.getErrorCode(), e.getMessage())))
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
