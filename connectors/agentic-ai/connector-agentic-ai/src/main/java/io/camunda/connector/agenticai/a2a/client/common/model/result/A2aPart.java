/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.model.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = A2aTextPart.class, name = "text"),
  @JsonSubTypes.Type(value = A2aDataPart.class, name = "object"),
  @JsonSubTypes.Type(value = A2aDocumentPart.class, name = "document")
})
public sealed interface A2aPart permits A2aTextPart, A2aDataPart, A2aDocumentPart {
  Map<String, Object> metadata();
}
