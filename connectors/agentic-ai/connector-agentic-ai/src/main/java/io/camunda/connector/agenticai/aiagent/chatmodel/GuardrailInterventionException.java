/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

import org.jspecify.annotations.Nullable;

/**
 * The response was blocked by a guardrail policy configured on the provider side. Distinct from
 * {@link ContentFilteredException}, which covers the provider's own built-in filtering: a guardrail
 * is something the user configured and can therefore act on.
 */
public final class GuardrailInterventionException extends ChatModelRejectedException {

  public GuardrailInterventionException(String message, @Nullable PartialResult partialResult) {
    super(message, partialResult);
  }
}
