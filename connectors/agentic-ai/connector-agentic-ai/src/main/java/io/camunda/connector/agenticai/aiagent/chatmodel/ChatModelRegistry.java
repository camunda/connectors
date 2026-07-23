/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

/** Resolves the {@link ChatModel} responsible for a given {@link ChatModelConfiguration}. */
public interface ChatModelRegistry {

  ChatModel resolve(ChatModelConfiguration configuration);
}
