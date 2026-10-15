/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.convert;

import io.a2a.spec.DataPart;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aDataPart;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aPart;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTextPart;

public class A2aPartConverterImpl implements A2aPartConverter {

  @Override
  public A2aPart convert(Part<?> part) {
    return switch (part) {
      case TextPart textPart -> new A2aTextPart(textPart.getText(), textPart.getMetadata());
      case DataPart dataPart -> new A2aDataPart(dataPart.getData(), dataPart.getMetadata());
      default ->
          throw new RuntimeException("Only text and data parts are supported in the response yet.");
    };
  }
}
