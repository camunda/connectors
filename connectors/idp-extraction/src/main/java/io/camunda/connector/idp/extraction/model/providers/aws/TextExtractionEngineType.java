/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers.aws;

/**
 * @deprecated Legacy IDP extraction provider model, used only by {@link
 *     io.camunda.connector.idp.extraction.ExtractionConnectorFunction} via {@code AwsProvider}.
 *     Retained for backwards compatibility; no removal currently planned.
 */
@Deprecated(since = "8.9")
public enum TextExtractionEngineType {
  AWS_TEXTRACT,
  APACHE_PDFBOX
}
