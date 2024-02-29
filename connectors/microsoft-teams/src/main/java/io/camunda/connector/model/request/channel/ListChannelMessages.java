/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import io.camunda.connector.model.request.MSTeamsRequestData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ListChannelMessages(
    @NotBlank String groupId,
    @NotBlank String channelId,
    String isExpand,
    @Pattern(regexp = "^([0-9])*$") String top)
    implements MSTeamsRequestData {
  public static final String EXPAND_VALUE = "replies";
}
