/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.suppliers.ObjectMapperSupplier;

public class RemoveNullFieldsUtil {

  private RemoveNullFieldsUtil() {}

  public static Object removeNullFieldsInObject(Object object) {
    ObjectMapper objectMapper = ObjectMapperSupplier.objectMapper();
    try {
      return objectMapper.readValue(objectMapper.writer().writeValueAsString(object), Object.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
