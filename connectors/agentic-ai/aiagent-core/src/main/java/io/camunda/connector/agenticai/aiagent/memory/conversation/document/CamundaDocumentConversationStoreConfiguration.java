/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.document;

import io.camunda.connector.agenticai.aiagent.model.request.ConversationStoreConfiguration;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of the Camunda document conversation store.
 *
 * @param timeToLive how long the conversation document is retained, or {@code null} to use the
 *     time-to-live default of the cluster
 * @param customProperties custom properties stored with the conversation document, empty if none
 */
public record CamundaDocumentConversationStoreConfiguration(
    @Nullable Duration timeToLive, Map<String, Object> customProperties)
    implements ConversationStoreConfiguration {

  @Override
  public String storeType() {
    return CamundaDocumentConversationStore.TYPE;
  }
}
