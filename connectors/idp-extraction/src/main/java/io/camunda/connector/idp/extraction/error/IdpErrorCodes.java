/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.error;

/** Error codes for IDP extraction connector operations. */
public interface IdpErrorCodes {

  /** Error code when LLM response parsing fails even after cleanup attempts. */
  String JSON_PARSING_FAILED = "JSON_PARSING_FAILED";

  /** Error code when LLM response is not a valid JSON object. */
  String INVALID_JSON_RESPONSE = "INVALID_JSON_RESPONSE";

  /** Error code when LLM response is neither a JSON object nor a string. */
  String INVALID_RESPONSE_FORMAT = "INVALID_RESPONSE_FORMAT";

  /** Error code when document extraction fails. */
  String EXTRACTION_FAILED = "EXTRACTION_FAILED";

  /** Error code when AI provider communication fails. */
  String AI_PROVIDER_ERROR = "AI_PROVIDER_ERROR";

  /** Error code when document classification fails. */
  String CLASSIFICATION_FAILED = "CLASSIFICATION_FAILED";
}
