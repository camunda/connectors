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
    return response.getCode() != null && !response.getCode().isBlank()
        ? response.getCode()
        : DEFAULT_ERROR_CODE;
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
    if (detail == null && contextMessages.isEmpty() && response.getRawBody() != null) {
      message.append(": ").append(response.getRawBody());
    }
    if (response.getRequestId() != null) {
      message.append(" [Request ID: ").append(response.getRequestId()).append("]");
    }
    return message.toString();
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
