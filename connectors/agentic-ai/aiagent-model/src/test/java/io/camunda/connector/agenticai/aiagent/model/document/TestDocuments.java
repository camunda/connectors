/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import io.camunda.connector.agenticai.aiagent.model.message.MessageId;

final class TestDocuments {

  /** The id of the message that a document of a test entered the conversation with. */
  static final MessageId MESSAGE_ID = MessageId.of("0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b");

  private TestDocuments() {}
}
