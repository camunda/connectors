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
package io.camunda.connector;

import io.camunda.connector.api.ConnectorInput;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.util.Objects;

public class MyConnectorRequest implements ConnectorInput {

  private String myProperty;

  // TODO: add request properties

  @Override
  public void validateWith(final Validator validator) {
    validator.require(myProperty, "my property");
    // TODO: validate request properties
  }

  @Override
  public void replaceSecrets(final SecretStore secretStore) {
    // TODO: replace secrets in request properties
    myProperty = secretStore.replaceSecret(myProperty);
  }

  public String getMyProperty() {
    return myProperty;
  }

  public void setMyProperty(final String myProperty) {
    this.myProperty = myProperty;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MyConnectorRequest that = (MyConnectorRequest) o;
    return Objects.equals(myProperty, that.myProperty);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myProperty);
  }

  @Override
  public String toString() {
    return "MyConnectorRequest{" + "myProperty='" + myProperty + '\'' + '}';
  }
}
