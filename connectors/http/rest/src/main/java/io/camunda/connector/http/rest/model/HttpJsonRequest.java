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
package io.camunda.connector.http.rest.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.auth.Authentication;

public class HttpJsonRequest extends HttpCommonRequest {

  @TemplateProperty(
      id = "authenticationConfiguration",
      label = "Authentication credential",
      group = "authentication",
      type = PropertyType.Configuration,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "authenticationConfiguration"),
      description =
          "Choose a reusable REST authentication credential. When set, it is bound as a whole to"
              + " the connector's 'authenticationConfiguration' input.")
  private RestAuthenticationConfiguration authenticationConfiguration;

  public RestAuthenticationConfiguration getAuthenticationConfiguration() {
    return authenticationConfiguration;
  }

  public void setAuthenticationConfiguration(
      RestAuthenticationConfiguration authenticationConfiguration) {
    this.authenticationConfiguration = authenticationConfiguration;
  }

  /**
   * Per-connector consumption of the bound authentication credential: when a credential
   * (configuration) is bound, its authentication takes precedence; the inline authentication is the
   * fallback. Per-field inline override is not modeled because authentication is a whole object.
   */
  @Override
  public Authentication getAuthentication() {
    if (authenticationConfiguration != null) {
      return authenticationConfiguration.authentication();
    }
    return super.getAuthentication();
  }
}
