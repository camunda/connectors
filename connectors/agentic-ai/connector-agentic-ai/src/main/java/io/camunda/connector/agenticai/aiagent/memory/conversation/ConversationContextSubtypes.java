/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import java.util.List;

/**
 * Service provider interface contributing {@link ConversationContext} subtypes to JSON
 * (de)serialization.
 *
 * <p>Implementations are registered in {@code
 * META-INF/services/io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContextSubtypes}
 * and discovered through {@link java.util.ServiceLoader}. Every listed class must be annotated with
 * {@link com.fasterxml.jackson.annotation.JsonTypeName}, which provides the {@code type} value used
 * in the serialized form.
 */
public interface ConversationContextSubtypes {

  List<Class<? extends ConversationContext>> subtypes();
}
