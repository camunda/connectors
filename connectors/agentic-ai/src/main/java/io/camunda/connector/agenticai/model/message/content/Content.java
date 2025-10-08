/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message.content;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = TextContent.class, name = "text"),
  @JsonSubTypes.Type(value = DocumentContent.class, name = "document"),
  @JsonSubTypes.Type(value = ObjectContent.class, name = "object")
})
public sealed interface Content permits TextContent, DocumentContent, ObjectContent {
  Map<String, Object> metadata();
}
