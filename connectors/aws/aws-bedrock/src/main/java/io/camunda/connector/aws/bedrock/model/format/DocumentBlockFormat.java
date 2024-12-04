/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.model.format;

public enum DocumentBlockFormat {
  // Document link:
  // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_DocumentBlock.html
  PDF,
  CSV,
  DOC,
  DOCX,
  XLS,
  XLSX,
  HTML,
  TXT,
  MD;
}
