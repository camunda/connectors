/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import io.camunda.connector.agenticai.common.annotation.AgenticAiRecord;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A document selected for the final response, as the agent context stores it. The agent context
 * keeps the selections in the order of selection. A selection with a key that is already used
 * replaces the earlier selection.
 *
 * @param key the key the process finds the document by; it starts with a letter or an underscore
 *     and continues with letters, digits, underscores or hyphens ({@link #KEY_PATTERN})
 * @param document the id of the document in the agent documents
 * @param description a description of the document, if any
 */
@AgenticAiRecord
public record ResponseDocumentSelection(
    String key, AgentDocumentId document, @Nullable String description)
    implements ResponseDocumentSelectionBuilder.With {

  /** The form of a key, as a regular expression. */
  public static final String KEY_PATTERN = "[A-Za-z_][A-Za-z0-9_-]*";

  private static final Pattern KEY = Pattern.compile(KEY_PATTERN);

  /** Whether {@code key} matches {@link #KEY_PATTERN}. */
  public static boolean isValidKey(String key) {
    return KEY.matcher(key).matches();
  }

  /**
   * @throws IllegalArgumentException if the key does not match {@link #KEY_PATTERN}
   */
  public ResponseDocumentSelection {
    Objects.requireNonNull(key, "key must not be null");
    if (!isValidKey(key)) {
      throw new IllegalArgumentException(
          "The key '%s' of a response document must match %s".formatted(key, KEY_PATTERN));
    }
    Objects.requireNonNull(document, "document must not be null");
  }
}
