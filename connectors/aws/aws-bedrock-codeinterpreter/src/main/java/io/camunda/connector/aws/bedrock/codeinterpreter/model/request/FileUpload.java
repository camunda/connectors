/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.codeinterpreter.model.request;

import io.camunda.connector.api.document.Document;
import jakarta.validation.constraints.NotNull;

/**
 * A document to upload into the session workspace.
 *
 * @param document the document to upload
 * @param fileName the name to store it under, defaults to the file name of the document
 */
public record FileUpload(@NotNull Document document, String fileName) {}
