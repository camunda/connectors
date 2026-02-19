/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.document;

import dev.langchain4j.data.message.Content;
import io.camunda.connector.api.document.Document;

public interface DocumentToContentConverter {
  Content convert(Document camundaDocument);
}
