/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import com.box.sdkgen.box.errors.BoxAPIError;
import com.box.sdkgen.box.errors.ResponseInfo;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.error.ConnectorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BoxErrors {

  private static final String DEFAULT_ERROR_CODE = "BOX_API_ERROR";

  private BoxErrors() {}

  public static RuntimeException translate(RuntimeException exception) {
    if (exception instanceof ConnectorException) {
      return exception;
    }
    for (Throwable t = exception; t != null; t = t.getCause()) {
      if (t instanceof BoxAPIError apiError && apiError.getResponseInfo() != null) {
        return new ConnectorException(
            errorCode(apiError.getResponseInfo()), describe(apiError.getResponseInfo()), apiError);
      }
    }
    return exception;
  }

  static String errorCode(ResponseInfo response) {
    if (response.getCode() != null && !response.getCode().isBlank()) {
      return response.getCode();
    }
    String authError = authenticateParam(response, "error");
    return authError != null ? authError : DEFAULT_ERROR_CODE;
  }

  static String describe(ResponseInfo response) {
    var message = new StringBuilder("Box API error (HTTP " + response.getStatusCode());
    if (response.getCode() != null && !response.getCode().isBlank()) {
      message.append(", ").append(response.getCode());
    }
    message.append(")");

    JsonNode body = response.getBody();
    String detail = text(body, "message");
    if (detail != null) {
      message.append(": ").append(detail);
    }
    List<String> contextMessages = contextMessages(response.getContextInfo());
    if (!contextMessages.isEmpty()) {
      message.append(" - ").append(String.join("; ", contextMessages));
    }
    if (detail == null && contextMessages.isEmpty()) {
      String rawBody = response.getRawBody();
      String fallback =
          rawBody != null && !rawBody.isBlank()
              ? rawBody
              : authenticateParam(response, "error_description");
      if (fallback != null) {
        message.append(": ").append(fallback);
      }
    }
    String requestId = response.getRequestId();
    if (requestId == null || requestId.isBlank()) {
      requestId = header(response, "box-request-id");
    }
    if (requestId != null && !requestId.isBlank()) {
      message.append(" [Request ID: ").append(requestId).append("]");
    }
    return message.toString();
  }

  private static String header(ResponseInfo response, String name) {
    if (response.getHeaders() == null) {
      return null;
    }
    return response.getHeaders().entrySet().stream()
        .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(name))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  /** Reads a parameter such as error="invalid_token" from the WWW-Authenticate header. */
  private static String authenticateParam(ResponseInfo response, String param) {
    String header = header(response, "www-authenticate");
    if (header == null) {
      return null;
    }
    Matcher matcher = Pattern.compile("(?:^|[\\s,])" + param + "=\"([^\"]*)\"").matcher(header);
    return matcher.find() && !matcher.group(1).isBlank() ? matcher.group(1) : null;
  }

  private static List<String> contextMessages(JsonNode contextInfo) {
    List<String> messages = new ArrayList<>();
    if (contextInfo == null) {
      return messages;
    }
    JsonNode errors = contextInfo.get("errors");
    if (errors != null && errors.isArray()) {
      for (JsonNode error : errors) {
        String text = text(error, "message");
        if (text == null) {
          text = text(error, "reason");
        }
        if (text != null) {
          messages.add(text);
        }
      }
    }
    String text = text(contextInfo, "message");
    if (messages.isEmpty() && text != null) {
      messages.add(text);
    }
    return messages;
  }

  private static String text(JsonNode node, String field) {
    if (node == null || !node.hasNonNull(field)) {
      return null;
    }
    String value = node.get(field).asText();
    return value.isBlank() ? null : value;
  }
}
