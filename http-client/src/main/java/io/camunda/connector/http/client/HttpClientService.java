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
package io.camunda.connector.http.client;

import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.blocklist.DefaultHttpBlocklistManager;
import io.camunda.connector.http.client.blocklist.HttpBlockListManager;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.client.cloudfunction.CloudFunctionService;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpClientResult;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientService {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientService.class);

  private final CloudFunctionService cloudFunctionService;

  private final HttpClient httpClient = new CustomApacheHttpClient();

  private final HttpBlockListManager httpBlocklistManager = new DefaultHttpBlocklistManager();

  public HttpClientService() {
    this(new CloudFunctionService());
  }

  public HttpClientService(CloudFunctionService cloudFunctionService) {
    this.cloudFunctionService = cloudFunctionService;
  }

  public HttpClientResult executeConnectorRequest(HttpClientRequest request) {
    return executeConnectorRequest(request, null);
  }

  public HttpClientResult executeConnectorRequest(
      HttpClientRequest request, @Nullable DocumentFactory documentFactory) {
    // Will throw ConnectorInputException if URL is blocked
    httpBlocklistManager.validateUrlAgainstBlocklist(request.getUrl());
    ExecutionEnvironment executionEnvironment =
        ExecutionEnvironment.from(
            cloudFunctionService.isCloudFunctionEnabled(),
            cloudFunctionService.isRunningInCloudFunction(),
            documentFactory);

    if (executionEnvironment instanceof ExecutionEnvironment.SaaSCluster) {
      // Wrap the request in a proxy request
      request = cloudFunctionService.toCloudFunctionRequest(request);
    }
    return executeRequest(request, executionEnvironment);
  }

  private HttpClientResult executeRequest(
      HttpClientRequest request, @Nullable ExecutionEnvironment executionEnvironment) {
    try {
      HttpClientResult jsonResult = httpClient.execute(request, executionEnvironment);
      LOGGER.debug("Connector returned result: {}", jsonResult);
      return jsonResult;
    } catch (ConnectorException e) {
      LOGGER.debug("Failed to execute request {}", request, e);
      if (executionEnvironment instanceof ExecutionEnvironment.SaaSCluster) {
        throw cloudFunctionService.parseCloudFunctionError(e);
      }
      throw e;
    } catch (final Exception e) {
      LOGGER.debug("Failed to execute request {}", request, e);
      throw new ConnectorException(
          "Failed to execute request: " + request + ". An error occurred: " + e.getMessage(), e);
    }
  }
}
