/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import com.microsoft.graph.models.ChannelMembershipType;
import io.camunda.connector.model.request.MSTeamsRequestData;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record CreateChannel(
    @NotBlank String groupId,
    @NotBlank String name,
    String description,
    @NotBlank String channelType,
    String owner)
    implements MSTeamsRequestData {

  @AssertTrue(message = "property owner is required")
  private boolean isOwnerValid() {
    if (!ChannelMembershipType.STANDARD.name().equalsIgnoreCase(channelType)) {
      return owner != null && !owner.isBlank();
    }
    return true;
  }
}
