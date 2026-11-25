/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.openai.core.JsonField;
import com.openai.core.JsonValue;
import com.openai.errors.BadRequestException;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the error code of an HTTP 400 answered by an OpenAI-compatible endpoint.
 *
 * <p>{@link BadRequestException#code()} cannot be used for this: it models the code as a string and
 * throws an {@code OpenAIInvalidDataException} when the endpoint answers another JSON type, for
 * example the numeric {@code "code": 400} of OpenRouter. This reads the raw error body instead, so
 * that any code type, a missing code or an unreadable body gives "no code" and never a failure.
 */
public final class OpenAiErrorCodes {

  private static final String CODE_FIELD = "code";

  private OpenAiErrorCodes() {}

  /**
   * The error code of the response as a string, empty if the code is missing or not a string. Never
   * throws.
   */
  public static Optional<String> codeOf(BadRequestException exception) {
    // JsonValue extends the raw JsonField, which erases the generics of its accessors
    final JsonField<?> body = exception.body();
    final Map<String, JsonValue> fields = body.asObject().orElse(Map.of());
    final JsonValue code = fields.get(CODE_FIELD);
    return code == null ? Optional.empty() : ((JsonField<?>) code).asString();
  }
}
