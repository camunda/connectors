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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectorExceptionMapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorExceptionMapper.class);
  private static final int MAX_BODY_LENGTH = 500;

  public static ConnectorException from(StreamingHttpResponse result) {
    String status = String.valueOf(result.status());
    String reason = Optional.ofNullable(result.reason()).orElse("[no reason]");
    Map<String, List<String>> headers = result.headers();
    Object body = null;

    StringBuilder messageBuilder =
        new StringBuilder("HTTP request failed with status code ")
            .append(status)
            .append(" (")
            .append(reason)
            .append(")");

    try (InputStream bodyStream = result.body()) {
      if (bodyStream != null) {
        String bodyString = new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
        appendBodyIfTextual(messageBuilder, bodyString, headers);
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
        .message(messageBuilder.toString())
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

  /**
   * Appends the response body to the given message if it is considered textual.
   *
   * <p>The body is trimmed and truncated to {@link #MAX_BODY_LENGTH} characters. The body is
   * appended if headers are null or if the Content-Type indicates a textual type.
   *
   * @param messageBuilder the StringBuilder containing the exception message
   * @param bodyString the raw response body
   * @param headers the response headers (may be null)
   */
  private static void appendBodyIfTextual(
      StringBuilder messageBuilder, String bodyString, Map<String, List<String>> headers) {
    String trimmedBody = bodyString.trim();
    if (StringUtils.isNotBlank(trimmedBody) && (headers == null || isTextualContentType(headers))) {
      messageBuilder
          .append(". Response body: ")
          .append(StringUtils.abbreviate(trimmedBody, MAX_BODY_LENGTH));
    }
  }

  /**
   * Checks if response headers are textual content type.
   *
   * <p>Textual content types: JSON, text, XML, and "application/problem+json".
   *
   * @param headers the response headers (may be null)
   * @return true if the Content-Type header indicates textual content
   */
  private static boolean isTextualContentType(Map<String, List<String>> headers) {
    if (headers == null) {
      return false;
    }
    String contentType = HeadersHelper.getHeaderIgnoreCase(headers, "Content-Type");
    if (contentType == null) {
      return false;
    }
    contentType = contentType.toLowerCase();
    return contentType.contains("application/json")
        || contentType.startsWith("text/")
        || contentType.contains("application/xml")
        || contentType.contains("application/problem+json");
  }
}
