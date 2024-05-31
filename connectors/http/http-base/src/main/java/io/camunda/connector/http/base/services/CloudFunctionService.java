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

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.auth.BearerAuthentication;
import io.camunda.connector.http.base.model.ErrorResponse;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.utils.CloudFunctionHelper;
import java.io.IOException;
import java.util.Map;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudFunctionService {

  private static final Logger LOG = LoggerFactory.getLogger(CloudFunctionService.class);

  /**
   * Wraps the given request into a new request that is targeted at the Google function to execute
   * the request remotely.
   *
   * @param request the request to be executed remotely
   * @param cloudFunctionUrl the URL of the Google function
   * @return the new request that is targeted at the Google function
   * @throws IOException if the request cannot be serialized
   */
  public HttpCommonRequest toCloudFunctionRequest(
      final HttpCommonRequest request, final String cloudFunctionUrl) throws IOException {
    // Using the JsonHttpContent cannot work with an element on the root content,
    // hence write it ourselves:
    String contentAsJson =
        ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(request);
    String token;
    try {
      token = CloudFunctionHelper.getOAuthToken(cloudFunctionUrl);
    } catch (Exception e) {
      LOG.error("Failure during OAuth authentication attempt for the Google cloud function", e);
      // this will be visible in Operate, so should hide the internal exception
      throw new ConnectorException(
          "Failure during OAuth authentication attempt for the Google cloud function");
    }
    HttpCommonRequest cloudFunctionRequest = new HttpCommonRequest();
    cloudFunctionRequest.setMethod(HttpMethod.POST);
    cloudFunctionRequest.setUrl(cloudFunctionUrl);
    cloudFunctionRequest.setBody(contentAsJson);
    cloudFunctionRequest.setHeaders(
        Map.of(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType()));
    cloudFunctionRequest.setConnectionTimeoutInSeconds(request.getConnectionTimeoutInSeconds());
    cloudFunctionRequest.setReadTimeoutInSeconds(request.getReadTimeoutInSeconds());
    cloudFunctionRequest.setAuthentication(new BearerAuthentication(token));

    return cloudFunctionRequest;
  }

  /**
   * Tries to parse the error response from the given exception and sets the error code and message.
   *
   * @param e the exception to be parsed
   * @param errorResponse the error response to be updated if possible
   */
  public ErrorResponse tryUpdateErrorUsingCloudFunctionError(
      ConnectorException e, ErrorResponse errorResponse) {
    ErrorResponse errorContent;
    try {
      errorContent =
          ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readValue(
              e.getMessage(), ErrorResponse.class);
      return new ErrorResponse(errorContent.errorCode(), errorContent.error());
    } catch (Exception ex) {
      LOG.warn("Error response cannot be parsed as JSON! Will use the plain message.");
      return errorResponse;
    }
  }
}
