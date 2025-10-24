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
package io.camunda.connector.http.client.exception;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorExceptionBuilder;
import io.camunda.connector.http.client.HttpClientObjectMapperSupplier;
import io.camunda.connector.http.client.mapper.StreamingHttpResponse;
import io.camunda.connector.http.client.utils.HeadersHelper;
import io.camunda.connector.http.client.utils.JsonHelper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectorExceptionMapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorExceptionMapper.class);

  public static ConnectorException from(StreamingHttpResponse result) {
    String status = String.valueOf(result.status());
    String reason = Optional.ofNullable(result.reason()).orElse("[no reason]");
    Map<String, List<String>> headers = result.headers();
    Object body = null;

    try (InputStream bodyStream = result.body()) {
      if (bodyStream != null) {
        var bodyString = new String(bodyStream.readAllBytes());
        if (JsonHelper.isJsonStringValid(bodyString)) {
          body = HttpClientObjectMapperSupplier.getCopy().readValue(bodyString, Map.class);
        } else {
          body = bodyString;
        }
      }
    } catch (IOException e) {
      LOGGER.error("Failed to read response body for error mapping", e);
    }

    Map<String, Object> response = new HashMap<>();
    response.put("headers", HeadersHelper.flattenHeaders(headers));
    response.put("body", body);
    return new ConnectorExceptionBuilder()
        .errorCode(status)
        .message(reason)
        .errorVariables(Map.of("response", response))
        .build();
  }

  public static ConnectorException from(Throwable e) {
    if (e instanceof ConnectorException) {
      return (ConnectorException) e;
    }
    return new ConnectorExceptionBuilder()
        .errorCode("500")
        .message("Error while executing an HTTP request: " + e.getMessage())
        .cause(e)
        .build();
  }
}
