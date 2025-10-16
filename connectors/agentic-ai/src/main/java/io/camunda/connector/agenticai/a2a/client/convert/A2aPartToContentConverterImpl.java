/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.convert;

import io.a2a.spec.DataPart;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;

public class A2aPartToContentConverterImpl implements A2aPartToContentConverter {

  @Override
  public Content convert(Part<?> part) {
    return switch (part) {
      case TextPart textPart -> new TextContent(textPart.getText(), textPart.getMetadata());
      case DataPart dataPart -> new ObjectContent(dataPart.getData(), dataPart.getMetadata());
      default ->
          throw new RuntimeException("Only text and data parts are supported in the response yet.");
    };
  }
}
