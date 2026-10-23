/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.testsupport;

import io.camunda.connector.agenticai.aiagent.model.request.ConversationStoreConfiguration;

/** A conversation store configuration for tests that do not run a store. */
public record TestConversationStoreConfiguration() implements ConversationStoreConfiguration {

  public static final String STORE_TYPE = "test-store";

  @Override
  public String storeType() {
    return STORE_TYPE;
  }
}
