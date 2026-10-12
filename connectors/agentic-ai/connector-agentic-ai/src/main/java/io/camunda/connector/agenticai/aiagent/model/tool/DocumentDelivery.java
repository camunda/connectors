/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

/**
 * How the documents a tool returns reach the model. A tool without a delivery mode delivers them as
 * {@link #REFERENCE}.
 */
public enum DocumentDelivery {
  /** The model sees the {@code <doc/>} tag and loads the content with {@code c8__get_document}. */
  REFERENCE,
  /**
   * The agent loads the content for the model, as if the model had called {@code c8__get_document},
   * for every document that passes the load guard.
   */
  INLINE
}
