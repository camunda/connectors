/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import com.google.api.client.util.Key;
import com.google.api.services.docs.v1.model.Request;
import java.util.List;
import java.util.Objects;

public class Variables {
  @Key private List<Request> requests;

  public List<Request> getRequests() {
    return requests;
  }

  public void setRequests(final List<Request> requests) {
    this.requests = requests;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Variables variables = (Variables) o;
    return Objects.equals(requests, variables.requests);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requests);
  }

  @Override
  public String toString() {
    return "Variables{" + "requests=" + requests + "}";
  }
}
