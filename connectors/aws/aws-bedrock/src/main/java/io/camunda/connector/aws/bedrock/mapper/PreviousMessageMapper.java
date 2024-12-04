/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.mapper;

import io.camunda.connector.aws.bedrock.model.PreviousMessage;
import java.util.List;

public final class PreviousMessageMapper {

  private PreviousMessageMapper() {}

  public static List<PreviousMessage> mapToPreviousMessage(List<Object> messages, String role) {
    return messages.stream().map(msg -> new PreviousMessage(msg, role)).toList();
  }
}
