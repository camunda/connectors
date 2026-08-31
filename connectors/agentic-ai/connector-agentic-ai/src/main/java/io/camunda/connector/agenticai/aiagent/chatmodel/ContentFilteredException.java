/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

import org.jspecify.annotations.Nullable;

/** The response was blocked or redacted by provider content filtering. */
public final class ContentFilteredException extends ChatModelRejectedException {

  public ContentFilteredException(String message, @Nullable PartialResult partialResult) {
    super(message, partialResult);
  }
}
