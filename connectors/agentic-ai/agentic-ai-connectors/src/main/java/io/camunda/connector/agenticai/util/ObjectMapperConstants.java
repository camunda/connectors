/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;

public class ObjectMapperConstants {
  public static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP_TYPE_REFERENCE =
      new TypeReference<>() {};

  private ObjectMapperConstants() {}
}
