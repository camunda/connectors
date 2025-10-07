/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.convert;

import static io.camunda.connector.agenticai.model.message.content.ObjectContent.objectContent;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;

import io.a2a.spec.DataPart;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aContent;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;

public class PartsToContentConverterImpl implements PartsToContentConverter {

  @Override
  public List<A2aContent> convert(List<Part<?>> parts) {
    if (CollectionUtils.isEmpty(parts)) {
      return List.of();
    }
    return parts.stream()
        .map(
            part ->
                switch (part) {
                  case TextPart textPart ->
                      new A2aContent(textContent(textPart.getText()), textPart.getMetadata());
                  case DataPart dataPart ->
                      new A2aContent(objectContent(dataPart.getData()), dataPart.getMetadata());
                  default ->
                      throw new RuntimeException(
                          "Only text and data parts are supported in the response yet.");
                })
        .toList();
  }
}
