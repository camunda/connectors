/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.model.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = A2aSendMessageResult.A2aMessageResult.class, name = "message"),
  @JsonSubTypes.Type(value = A2aSendMessageResult.A2aTaskResult.class, name = "task")
})
public sealed interface A2aSendMessageResult extends A2aClientResult
    permits A2aSendMessageResult.A2aMessageResult, A2aSendMessageResult.A2aTaskResult {

  record A2aMessageResult(A2aMessage message) implements A2aSendMessageResult {}

  record A2aTaskResult(A2aTask task) implements A2aSendMessageResult {}
}
