/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Configuration of a custom conversation store implementation. */
public interface CustomConversationStoreConfiguration extends ConversationStoreConfiguration {

  /** Parameters passed to the custom conversation store implementation. */
  Map<String, Object> parameters();

  /** Creates the configuration of a custom store with the given type and parameters. */
  static CustomConversationStoreConfiguration of(String storeType, Map<String, Object> parameters) {
    return new Standard(storeType, Collections.unmodifiableMap(new LinkedHashMap<>(parameters)));
  }

  record Standard(String storeType, Map<String, Object> parameters)
      implements CustomConversationStoreConfiguration {}
}
