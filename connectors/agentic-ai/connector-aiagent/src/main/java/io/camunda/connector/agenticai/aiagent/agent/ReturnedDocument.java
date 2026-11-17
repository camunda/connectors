/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.common.annotation.AgenticAiRecord;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import org.jspecify.annotations.Nullable;

/**
 * A document that the agent returns with its final response, in {@code responseDocuments} under its
 * key.
 *
 * @param document the Camunda document reference of the document
 * @param description a short description of the document, or {@code null}
 */
@AgenticAiRecord
public record ReturnedDocument(CamundaDocumentReferenceModel document, @Nullable String description)
    implements ReturnedDocumentBuilder.With {}
