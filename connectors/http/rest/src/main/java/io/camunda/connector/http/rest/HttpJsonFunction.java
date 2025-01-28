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

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.rest.model.HttpJsonRequest;

@OutboundConnector(
    name = "HTTP REST",
    inputVariables = {
      "url",
      "method",
      "authentication",
      "headers",
      "queryParameters",
      "skipEncoding",
      "connectionTimeoutInSeconds",
      "readTimeoutInSeconds",
      "writeTimeoutInSeconds",
      "body",
      "storeResponse",
      "groupSetCookieHeaders"
    },
    type = HttpJsonFunction.TYPE)
@ElementTemplate(
    id = "io.camunda.connectors.HttpJson.v2",
    name = "REST Outbound Connector",
    description = "Invoke REST API",
    inputDataClass = HttpJsonRequest.class,
    outputDataClass = HttpCommonResult.class,
    version = 10,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "endpoint", label = "HTTP endpoint"),
      @PropertyGroup(id = "timeout", label = "Connection timeout"),
      @PropertyGroup(id = "payload", label = "Payload")
    },
    documentationRef = "https://docs.camunda.io/docs/components/connectors/protocol/rest/",
    icon = "icon.svg")
public class HttpJsonFunction implements OutboundConnectorFunction {

  public static final String TYPE = "io.camunda:http-json:1";

  protected final HttpService httpService;

  public HttpJsonFunction() {
    this(new HttpService());
  }

  HttpJsonFunction(HttpService httpService) {
    this.httpService = httpService;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) {
    final var request = extractRequest(context);
    try {
      final var result = httpService.executeConnectorRequest(request, context);
      return extractResponse(result);
    } catch (RuntimeException e) {
      throw extractException(e, context);
    }
  }

  protected HttpJsonRequest extractRequest(final OutboundConnectorContext context) {
    return context.bindVariables(HttpJsonRequest.class);
  }

  protected HttpCommonResult extractResponse(final HttpCommonResult result) {
    return result;
  }

  protected RuntimeException extractException(
      final RuntimeException exception, final OutboundConnectorContext context) {
    return exception;
  }
}
