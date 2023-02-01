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

import com.google.api.client.http.HttpRequestFactory;
import com.google.gson.Gson;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.http.components.GsonComponentSupplier;
import io.camunda.connector.http.components.HttpTransportComponentSupplier;
import io.camunda.connector.http.constants.Constants;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.impl.config.ConnectorConfigurationUtil;
import java.io.IOException;

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

  private final Gson gson;
  private final HttpService httpService;

  public HttpJsonFunction() {
    this(ConnectorConfigurationUtil.getProperty(Constants.PROXY_FUNCTION_URL_ENV_NAME));
  }

  public HttpJsonFunction(String proxyFunctionUrl) {
    this(
        GsonComponentSupplier.gsonInstance(),
        HttpTransportComponentSupplier.httpRequestFactoryInstance(),
        proxyFunctionUrl);
  }

  public HttpJsonFunction(
      final Gson gson, final HttpRequestFactory requestFactory, String proxyFunctionUrl) {
    this.httpService = new HttpService(gson, requestFactory, proxyFunctionUrl);
    this.gson = gson;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) throws IOException {
    final var json = context.getVariables();
    final var request = gson.fromJson(json, HttpJsonRequest.class);

    context.validate(request);
    context.replaceSecrets(request);

    return httpService.executeConnectorRequest(request);
  }
}
