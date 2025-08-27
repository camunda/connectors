/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.agenticai.model.message.content.Content;

public interface ContentConverter {
  /** Converts a {@link Content} to a LangChain4j {@link dev.langchain4j.data.message.Content}. */
  dev.langchain4j.data.message.Content convertToContent(Content content)
      throws JsonProcessingException;

  /** Converts a result to a string representation. */
  String convertToString(Object content) throws JsonProcessingException;
}
