/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.models;

import java.time.OffsetDateTime;
import java.util.*;

public record Email(
    EmailBody body,
    String messageId,
    String subject,
    List<Header> headers,
    String from,
    List<String> to,
    List<String> cc,
    OffsetDateTime sentAt,
    OffsetDateTime receivedAt,
    Integer size) {}
