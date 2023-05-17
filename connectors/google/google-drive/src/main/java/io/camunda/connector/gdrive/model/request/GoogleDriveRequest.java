/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *             under one or more contributor license agreements. Licensed under a proprietary license.
 *             See the License.txt file for more information. You may not use this file
 *             except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import com.google.api.client.util.Key;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.google.model.GoogleBaseRequest;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class GoogleDriveRequest extends GoogleBaseRequest {

  @Key @Valid @NotNull @Secret private Resource resource;

  public Resource getResource() {
    return resource;
  }

  public void setResource(final Resource resource) {
    this.resource = resource;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    GoogleDriveRequest that = (GoogleDriveRequest) o;
    return Objects.equals(authentication, that.authentication)
        && Objects.equals(resource, that.resource);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, resource);
  }

  @Override
  public String toString() {
    return "GoogleDriveRequest{"
        + "authentication="
        + authentication
        + ", resource="
        + resource
        + '}';
  }
}
