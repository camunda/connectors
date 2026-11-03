/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk.convert;

import io.camunda.connector.agenticai.a2a.client.common.model.part.A2aDataPart;
import io.camunda.connector.agenticai.a2a.client.common.model.part.A2aPart;
import io.camunda.connector.agenticai.a2a.client.common.model.part.A2aTextPart;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;

public class A2aPartConverterImpl implements A2aPartConverter {

  @Override
  public A2aPart convert(Part<?> part) {
    return switch (part) {
      case TextPart textPart -> new A2aTextPart(textPart.text(), textPart.metadata());
      case DataPart dataPart -> {
        final var data = dataPart.data();
        if (data == null) {
          throw new RuntimeException("Data part without data is not supported.");
        }
        yield new A2aDataPart(data, dataPart.metadata());
      }
      default ->
          throw new RuntimeException("Only text and data parts are supported in the response yet.");
    };
  }
}
