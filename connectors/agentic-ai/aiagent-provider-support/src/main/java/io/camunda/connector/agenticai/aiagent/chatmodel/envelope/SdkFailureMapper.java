/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.envelope;

import java.util.Optional;

/**
 * Recognizes the failures of one provider SDK. Every provider supplies its own mapper to the {@link
 * ModelCallEnvelope} wrapped around its chat model.
 */
@FunctionalInterface
public interface SdkFailureMapper {

  /**
   * Returns the failure if the given throwable is a failure the provider SDK reports for a response
   * the provider has sent, or empty for any other failure (e.g. a connection failure).
   */
  Optional<SdkFailure> map(Throwable failure);
}
