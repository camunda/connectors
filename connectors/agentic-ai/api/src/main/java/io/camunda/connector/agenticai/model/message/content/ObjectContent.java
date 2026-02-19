/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ObjectContent(
    Object content, @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements Content {
  public ObjectContent {
    if (content == null) {
      throw new IllegalArgumentException("Content cannot be null");
    }
  }

  public static ObjectContent objectContent(Object content) {
    return new ObjectContent(content, null);
  }
}
