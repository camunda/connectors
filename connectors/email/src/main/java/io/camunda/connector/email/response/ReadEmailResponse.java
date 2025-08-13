/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.response;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.email.client.jakarta.models.Header;
import java.time.OffsetDateTime;
import java.util.List;

public record ReadEmailResponse(
    String messageId,
    String fromAddress,
    List<Header> headers,
    String subject,
    Integer size,
    String plainTextBody,
    String htmlBody,
    List<Document> attachments,
    OffsetDateTime receivedDateTime) {}
