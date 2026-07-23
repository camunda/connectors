/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model.result;

/** Connector-owned mirror of the v2 {@code DocumentMetadata} shape ({@code pages} only). */
public record DocumentMetadata(Integer pages) {

  public static DocumentMetadata from(
      final software.amazon.awssdk.services.textract.model.DocumentMetadata metadata) {
    if (metadata == null) {
      return null;
    }
    return new DocumentMetadata(metadata.pages());
  }
}
