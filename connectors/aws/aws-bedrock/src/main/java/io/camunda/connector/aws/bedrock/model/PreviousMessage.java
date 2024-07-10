/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.model;

import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;

public class PreviousMessage {
  private String message;
  private ConversationRole role;

  public PreviousMessage(String message, ConversationRole role) {
    this.message = message;
    this.role = role;
  }

  public PreviousMessage() {}

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public ConversationRole getRole() {
    return role;
  }

  public void setRole(ConversationRole role) {
    this.role = role;
  }
}
