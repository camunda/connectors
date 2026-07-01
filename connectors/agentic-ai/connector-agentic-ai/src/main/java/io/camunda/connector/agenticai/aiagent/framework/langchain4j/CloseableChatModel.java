/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import dev.langchain4j.model.chat.ChatModel;

/**
 * A {@link ChatModel} that owns a closeable resource (e.g. an HTTP connection pool) and must be
 * closed after use. Declares {@code close()} without a checked exception to align with AWS SDK's
 * {@code SdkAutoCloseable}.
 */
public interface CloseableChatModel extends ChatModel, AutoCloseable {
  @Override
  void close();
}
