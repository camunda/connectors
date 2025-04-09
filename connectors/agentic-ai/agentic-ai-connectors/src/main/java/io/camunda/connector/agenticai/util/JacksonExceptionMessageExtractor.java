/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util;

import com.fasterxml.jackson.core.JsonProcessingException;

public class JacksonExceptionMessageExtractor {
  private JacksonExceptionMessageExtractor() {}

  public static String humanReadableJsonProcessingExceptionMessage(
      JsonProcessingException processingException) {
    StringBuilder sb = new StringBuilder();
    sb.append(processingException.getOriginalMessage());

    if (processingException.getLocation() != null) {
      sb.append(" (");
      processingException.getLocation().appendOffsetDescription(sb);
      sb.append(")");
    }

    return sb.toString();
  }
}
