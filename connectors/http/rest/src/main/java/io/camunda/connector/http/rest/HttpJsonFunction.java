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
package io.camunda.connector.http.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.HttpRequestFactory;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.config.ConnectorConfigurationUtil;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.feel.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.components.HttpTransportComponentSupplier;
import io.camunda.connector.http.base.constants.Constants;
import io.camunda.connector.http.base.services.HttpService;
import io.camunda.connector.http.rest.model.HttpJsonRequest;
import java.io.IOException;
import java.security.GeneralSecurityException;

@OutboundConnector(
    name = "HTTPJSON",
    inputVariables = {
      "url",
      "method",
      "authentication",
      "headers",
      "queryParameters",
      "connectionTimeoutInSeconds",
      "body"
    },
    type = "io.camunda:http-json:1")
public class HttpJsonFunction implements OutboundConnectorFunction {

  private final HttpService httpService;

  public HttpJsonFunction() {
    this(ConnectorConfigurationUtil.getProperty(Constants.PROXY_FUNCTION_URL_ENV_NAME));
  }

  public HttpJsonFunction(String proxyFunctionUrl) {
    this(
        ConnectorsObjectMapperSupplier.getCopy(),
        HttpTransportComponentSupplier.httpRequestFactoryInstance(),
        proxyFunctionUrl);
  }

  public HttpJsonFunction(
      final ObjectMapper objectMapper,
      final HttpRequestFactory requestFactory,
      final String proxyFunctionUrl) {
    this.httpService = new HttpService(objectMapper, requestFactory, proxyFunctionUrl);
  }

  @Override
  public Object execute(final OutboundConnectorContext context)
      throws IOException, InstantiationException, IllegalAccessException, GeneralSecurityException {
    final var request = context.bindVariables(HttpJsonRequest.class);
    return httpService.executeConnectorRequest(request);
  }
}
