/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.model.request.MSTeamsRequestData;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;
import okhttp3.Request;

public class ListChannels extends MSTeamsRequestData {

  @NotBlank @Secret private String groupId;
  @Secret private String filter;

  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    final var request = graphClient.teams(groupId).allChannels().buildRequest();
    if (filter != null) {
      request.filter(filter);
    }
    return request.get();
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(final String groupId) {
    this.groupId = groupId;
  }

  public String getFilter() {
    return filter;
  }

  public void setFilter(final String filter) {
    this.filter = filter;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ListChannels that = (ListChannels) o;
    return Objects.equals(groupId, that.groupId) && Objects.equals(filter, that.filter);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, filter);
  }

  @Override
  public String toString() {
    return "ListAllChannel{" + "groupId='" + groupId + "'" + ", filter='" + filter + "'" + "}";
  }
}
