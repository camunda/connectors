/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import io.camunda.connector.model.OrderBy;
import io.camunda.connector.model.request.MSTeamsRequestData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ListMessagesInChat(
    @NotBlank String chatId,
    String filter,
    @NotNull OrderBy orderBy,
    @Pattern(regexp = "^([1-9])|([1-4][0-9])|(50)$") String top)
    implements MSTeamsRequestData {}
