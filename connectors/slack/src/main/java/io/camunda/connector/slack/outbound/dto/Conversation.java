/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.dto;

public record Conversation(String id, String name) {
  public Conversation(com.slack.api.model.Conversation conversation) {
    this(conversation.getId(), conversation.getName());
  }
}
