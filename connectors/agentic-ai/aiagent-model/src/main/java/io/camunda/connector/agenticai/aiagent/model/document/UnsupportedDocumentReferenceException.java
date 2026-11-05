/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

/**
 * The reference of a document is of a type the agent cannot identify: not a Camunda, an external or
 * an inline reference with content.
 */
public class UnsupportedDocumentReferenceException extends IllegalArgumentException {

  public UnsupportedDocumentReferenceException(String message) {
    super(message);
  }
}
