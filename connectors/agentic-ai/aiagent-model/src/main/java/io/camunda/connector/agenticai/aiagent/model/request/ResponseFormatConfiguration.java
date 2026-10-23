/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The format the model is asked to respond in and the response is mapped from. */
public sealed interface ResponseFormatConfiguration {

  /**
   * Free text, optionally parsed as JSON afterwards.
   *
   * @param parseJson whether to parse the text content as JSON, for models without a native JSON
   *     mode
   */
  record TextResponseFormatConfiguration(boolean parseJson)
      implements ResponseFormatConfiguration {}

  /**
   * JSON, optionally structured according to a schema.
   *
   * @param schema the JSON schema of the response, or {@code null} for none
   * @param schemaName the name of the schema, to make the model aware of the expected output
   */
  record JsonResponseFormatConfiguration(@Nullable Map<String, Object> schema, String schemaName)
      implements ResponseFormatConfiguration {

    public static final String DEFAULT_SCHEMA_NAME = "Response";

    /**
     * {@code false} both when no schema was configured ({@code null}) and when one was configured
     * as an empty object -- an empty schema constrains nothing, so it is treated the same as no
     * schema at all.
     */
    public boolean hasSchema() {
      return schema != null && !schema.isEmpty();
    }
  }
}
