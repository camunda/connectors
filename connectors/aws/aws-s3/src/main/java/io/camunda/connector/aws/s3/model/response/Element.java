/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.s3.model.response;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReturnChoice;

public interface Element {
  record DocumentContent(Document document) implements Element {}

  record StringContent(String content) implements Element {}

  record JsonContent(Object content) implements Element {}

  static Element of(DocumentReturnChoice choice, Object converted) {
    return switch (choice) {
      case DOCUMENT -> new DocumentContent((Document) converted);
      case TEXT -> new StringContent((String) converted);
      case JSON -> new JsonContent(converted);
    };
  }
}
