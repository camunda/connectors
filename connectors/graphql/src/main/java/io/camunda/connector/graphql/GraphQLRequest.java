/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.graphql;

import io.camunda.connector.api.annotation.Secret;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class GraphQLRequest {

  @NotEmpty private String myProperty;

  @Valid @NotNull @Secret private Authentication authentication;

  // TODO: add request properties

  public String getMyProperty() {
    return myProperty;
  }

  public void setMyProperty(final String myProperty) {
    this.myProperty = myProperty;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GraphQLRequest that = (GraphQLRequest) o;
    return myProperty.equals(that.myProperty) && authentication.equals(that.authentication);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myProperty, authentication);
  }

  @Override
  public String toString() {
    return "MyConnectorRequest{"
        + "myProperty='"
        + myProperty
        + '\''
        + ", authentication="
        + authentication
        + '}';
  }
}
