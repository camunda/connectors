/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

/**
 * A parameter of a tool has the type {@value DocumentParameter#TYPE_DOCUMENT} where no document
 * parameter can sit (see {@link DocumentParameter#fromDocumentType}).
 */
public class MisplacedDocumentTypeException extends RuntimeException {

  public MisplacedDocumentTypeException(String parameterName) {
    super(
        "Parameter '%s': The type 'document' can only be the type of a parameter, of the items of an array or of an object property. Use a separate parameter for a document."
            .formatted(parameterName));
  }
}
