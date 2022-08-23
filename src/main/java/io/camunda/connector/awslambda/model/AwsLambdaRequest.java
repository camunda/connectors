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

package io.camunda.connector.awslambda.model;

import io.camunda.connector.api.ConnectorInput;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;

public class AwsLambdaRequest implements ConnectorInput {

  private AuthenticationRequestData authentication;
  private FunctionRequestData function;

  @Override
  public void validateWith(final Validator validator) {
    validator.require(authentication, "AuthenticationRequestData");
    validator.require(function, "FunctionRequestData");
    authentication.validateWith(validator);
    function.validateWith(validator);
  }

  @Override
  public void replaceSecrets(final SecretStore secretStore) {
    authentication.replaceSecrets(secretStore);
    function.replaceSecrets(secretStore);
  }

  public AuthenticationRequestData getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final AuthenticationRequestData authentication) {
    this.authentication = authentication;
  }

  public FunctionRequestData getFunction() {
    return function;
  }

  public void setFunction(final FunctionRequestData function) {
    this.function = function;
  }

  @Override
  public String toString() {
    return "AwsLambdaRequest{"
        + "authentication="
        + authentication
        + ", function="
        + function
        + "}";
  }
}
