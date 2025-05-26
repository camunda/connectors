/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.domain.model.message;

import io.camunda.connector.agenticai.domain.model.message.content.Content;
import java.util.List;

public interface ContentMessage {
  List<Content> content();
}
