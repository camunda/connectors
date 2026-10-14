/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the schema maps of the definitions of the built-in runtime tools. */
final class OrderedMaps {

  private OrderedMaps() {}

  /**
   * An insertion-ordered map built through the canonical constructor of the record: the definition
   * of a tool is part of the configuration fingerprint and of the request sent to the model, which
   * need the same bytes in every JVM (the record builder copies maps into an immutable map of
   * salted order).
   */
  static Map<String, Object> ordered(Object... keysAndValues) {
    final var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return Collections.unmodifiableMap(map);
  }
}
