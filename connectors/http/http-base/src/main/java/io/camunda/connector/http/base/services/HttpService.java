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

import static io.camunda.connector.http.base.constants.Constants.PROXY_FUNCTION_URL_ENV_NAME;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.base.blocklist.DefaultHttpBlocklistManager;
import io.camunda.connector.http.base.blocklist.HttpBlockListManager;
import io.camunda.connector.http.base.components.HttpClient;
import io.camunda.connector.http.base.components.apache.CustomApacheHttpClient;
import io.camunda.connector.http.base.model.ErrorResponse;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpService {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpService.class);

  private final String proxyFunctionUrl = System.getenv(PROXY_FUNCTION_URL_ENV_NAME);

  private final RemoteExecutionService remoteExecutionService = new RemoteExecutionService();
  private final HttpClient httpClient = CustomApacheHttpClient.getDefault();
  private final HttpBlockListManager httpBlocklistManager = new DefaultHttpBlocklistManager();

  public HttpCommonResult executeConnectorRequest(HttpCommonRequest request) throws Exception {
    // Will throw ConnectorInputException if URL is blocked
    httpBlocklistManager.validateUrlAgainstBlocklist(request.getUrl());
    boolean remoteExecutionEnabled = isRemoteExecutionEnabled();

    if (remoteExecutionEnabled) {
      // Wrap the request in a proxy request
      request = remoteExecutionService.toRemotelyExecutableRequest(request, proxyFunctionUrl);
    }
    return executeRequest(request, remoteExecutionEnabled);
  }

  private HttpCommonResult executeRequest(
      HttpCommonRequest request, boolean remoteExecutionEnabled) {
    try {
      HttpCommonResult jsonResult = httpClient.execute(request, remoteExecutionEnabled);
      LOGGER.debug("Connector returned result: {}", jsonResult);
      return jsonResult;
    } catch (ConnectorException e) {
      LOGGER.debug("Failed to execute request {}", request, e);
      var errorResponse = new ErrorResponse(e.getErrorCode(), e.getMessage());
      if (remoteExecutionEnabled) {
        // Will try to parse the exception message as a ErrorResponse
        remoteExecutionService.tryUpdateErrorUsingRemoteExecutionError(e, errorResponse);
      }
      throw new ConnectorException(errorResponse.getErrorCode(), errorResponse.getError(), e);
    } catch (final Exception e) {
      LOGGER.debug("Failed to execute request {}", request, e);
      throw new ConnectorException(
          "Failed to execute request: " + request + ". An error occurred: " + e.getMessage(), e);
    }
  }

  /**
   * Check if our internal Google Function should be used to execute the {@link HttpCommonRequest}
   * remotely.
   */
  private boolean isRemoteExecutionEnabled() {
    return proxyFunctionUrl != null;
  }
}
