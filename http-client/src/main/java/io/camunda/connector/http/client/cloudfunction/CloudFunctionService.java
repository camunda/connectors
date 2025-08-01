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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorExceptionBuilder;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.client.model.ErrorResponse;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpMethod;
import io.camunda.connector.http.client.model.auth.BearerAuthentication;
import io.camunda.connector.http.client.utils.DocumentHelper;
import io.camunda.document.Document;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudFunctionService {

  private static final Logger LOG = LoggerFactory.getLogger(CloudFunctionService.class);
  private static final String PROXY_FUNCTION_URL_ENV_NAME = "CAMUNDA_CONNECTOR_HTTP_PROXY_URL";
  private static final int NO_TIMEOUT = 0;

  /**
   * Environment variable to check if the current execution is running in a Google Cloud Function.
   */
  private static final String CLOUD_FUNCTION_MARKER_VARIABLE =
      "CAMUNDA_CONNECTOR_HTTP_BLOCK_URL_GCP_META_DATA_INTERNAL";

  private final String proxyFunctionUrl = System.getenv(PROXY_FUNCTION_URL_ENV_NAME);
  private final CloudFunctionCredentials credentials;

  public CloudFunctionService() {
    this(new CloudFunctionCredentials());
  }

  public CloudFunctionService(CloudFunctionCredentials credentials) {
    this.credentials = credentials;
  }

  /**
   * Wraps the given request into a new request that is targeted at the Google function to execute
   * the request remotely.
   *
   * @param request the request to be executed remotely
   * @return the new request that is targeted at the Google function
   */
  public HttpClientRequest toCloudFunctionRequest(final HttpClientRequest request) {
    try {
      String token = credentials.getOAuthToken(getProxyFunctionUrl());
      return createCloudFunctionRequest(request, token);
    } catch (IOException e) {
      LOG.error("Failed to serialize the request to JSON: {}", request, e);
      throw new ConnectorException("Failed to serialize the request to JSON: " + request, e);
    } catch (Exception e) {
      LOG.error("Failure during OAuth authentication attempt for the Google cloud function", e);
      // this will be visible in Operate, so should hide the internal exception
      throw new ConnectorException(
          "Failure during OAuth authentication attempt for the Google cloud function");
    }
  }

  /**
   * Tries to parse the error response from the given exception and sets the error code, message,
   * and errorVariables.
   *
   * @param e the parsed exception
   */
  public ConnectorException parseCloudFunctionError(ConnectorException e) {
    ErrorResponse errorContent;
    try {
      Map<String, Object> response = (Map<String, Object>) e.getErrorVariables().get("response");
      errorContent = (ErrorResponse) response.get("body");
    } catch (Exception ex) {
      LOG.warn("Error response cannot be parsed as JSON! Will use the plain message.");
      errorContent = new ErrorResponse(e.getErrorCode(), e.getMessage(), e.getErrorVariables());
    }

    return new ConnectorExceptionBuilder()
        .message(errorContent.error())
        .errorVariables(errorContent.errorVariables())
        .errorCode(errorContent.errorCode())
        .build();
  }

  /**
   * Check if our internal Google Function should be used to execute the {@link HttpClientRequest}
   * remotely.
   */
  public boolean isCloudFunctionEnabled() {
    return proxyFunctionUrl != null;
  }

  /** Check if the current execution is running in a Google Cloud Function. */
  public boolean isRunningInCloudFunction() {
    return System.getenv(CLOUD_FUNCTION_MARKER_VARIABLE) != null;
  }

  public String getProxyFunctionUrl() {
    return proxyFunctionUrl;
  }

  private HttpClientRequest createCloudFunctionRequest(HttpClientRequest request, String token)
      throws JsonProcessingException {
    Object parsedBody = prepareBodyForCloudFunction(request);
    request.setBody(parsedBody);
    String contentAsJson = ConnectorsObjectMapperSupplier.getCopy().writeValueAsString(request);
    HttpClientRequest cloudFunctionRequest = new HttpClientRequest();
    cloudFunctionRequest.setMethod(HttpMethod.POST);
    cloudFunctionRequest.setUrl(getProxyFunctionUrl());
    cloudFunctionRequest.setBody(contentAsJson);
    cloudFunctionRequest.setStoreResponse(request.isStoreResponse());
    cloudFunctionRequest.setReadTimeoutInSeconds(NO_TIMEOUT);
    cloudFunctionRequest.setHeaders(
        Map.of(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType()));
    cloudFunctionRequest.setAuthentication(new BearerAuthentication(token));
    return cloudFunctionRequest;
  }

  private boolean isMultipartContentType(HttpClientRequest request) {
    return request
        .getHeader(HttpHeaders.CONTENT_TYPE)
        .map(contentType -> contentType.startsWith(ContentType.MULTIPART_FORM_DATA.getMimeType()))
        .orElse(false);
  }

  /**
   * Prepare the body for the Google Cloud Function. If the body is a multipart form data, it will
   * be parsed and Documents will be converted into {@link CloudFunctionFilePart}.
   */
  private Object prepareBodyForCloudFunction(HttpClientRequest request) {
    boolean isMultipartContentType = isMultipartContentType(request);
    Object body = request.getBody();
    if (isMultipartContentType && body instanceof Map mapBody) {
      return parseDocumentsAndConvertToFileParts(mapBody);
    }
    return new DocumentHelper().parseDocumentsInBody(body, Document::asByteArray);
  }

  private Map<String, Object> parseDocumentsAndConvertToFileParts(Map<String, ?> body) {
    return body.entrySet().stream()
        .map(
            entry ->
                Map.entry(
                    entry.getKey(),
                    new DocumentHelper()
                        .parseDocumentsInBody(
                            entry.getValue(),
                            document ->
                                new CloudFunctionFilePart(
                                    entry.getKey(),
                                    document.metadata().getFileName(),
                                    document.asByteArray(),
                                    document.metadata().getContentType()))))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
