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
package io.camunda.connector.runtime.instances.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.runtime.core.http.DefaultInstancesUrlBuilder;
import io.camunda.connector.runtime.core.http.InstanceForwardingHttpClient;
import io.camunda.connector.runtime.instances.reducer.ReducerRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultInstanceForwardingService implements InstanceForwardingService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultInstanceForwardingService.class);

  private final String hostname;

  private final ReducerRegistry reducerRegistry = new ReducerRegistry();

  private final InstanceForwardingHttpClient instanceForwardingHttpClient;

  public DefaultInstanceForwardingService(
      int appPort, String headlessServiceUrl, String hostname, ObjectMapper objectMapper) {
    this(
        new InstanceForwardingHttpClient(
            new DefaultInstancesUrlBuilder(appPort, headlessServiceUrl), objectMapper),
        hostname);
  }

  public DefaultInstanceForwardingService(
      InstanceForwardingHttpClient instanceForwardingHttpClient, String hostname) {
    this.instanceForwardingHttpClient = instanceForwardingHttpClient;
    this.hostname = hostname;
  }

  @Override
  public <T> List<T> forward(HttpServletRequest request, TypeReference<T> responseType) {
    String method = request.getMethod();
    String path = request.getRequestURI();
    if (request.getQueryString() != null) {
      path += "?" + request.getQueryString();
    }
    try (var reader = request.getReader()) {
      String body = reader.lines().collect(Collectors.joining(System.lineSeparator()));

      Map<String, String> headers =
          Collections.list(request.getHeaderNames()).stream()
              .collect(Collectors.toMap(headerName -> headerName, request::getHeader));

      if (hostname == null) {
        LOGGER.error(
            "HOSTNAME environment variable (or 'camunda.connector.hostname' property) is not set. Cannot use instances forwarding.");
        throw new RuntimeException(
            "HOSTNAME environment variable (or 'camunda.connector.hostname' property) is not set. Cannot use instances forwarding.");
      }

      return instanceForwardingHttpClient.execute(
          method, path, body, headers, responseType, hostname);
    } catch (Exception e) {
      LOGGER.error("Error forwarding request to instances: {}", e.getMessage(), e);
      throw new RuntimeException("Error forwarding request to instances: " + e.getMessage(), e);
    }
  }

  @Override
  public <T> T reduce(List<T> responses, TypeReference<T> responseType) {
    if (responses == null || responses.isEmpty()) {
      return null;
    }
    var reducer = reducerRegistry.getReducer(responseType);

    if (reducer == null) {
      LOGGER.error("No reducer found for response type {}.", responseType.getType());
      throw new RuntimeException("No reducer found for response type: " + responseType.getType());
    }

    return responses.stream().reduce(null, reducer::reduce);
  }
}
