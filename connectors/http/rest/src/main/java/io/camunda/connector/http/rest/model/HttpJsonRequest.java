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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.auth.Authentication;
import io.camunda.connector.http.base.model.auth.RestAuthenticationConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

public class HttpJsonRequest extends HttpCommonRequest {

  @TemplateProperty(
      id = "authenticationConfiguration",
      label = "Authentication credential",
      group = "authentication",
      type = PropertyType.Configuration,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "authenticationConfiguration"),
      description =
          "Choose a reusable REST authentication credential, or configure one-time"
              + " authentication parameters below.")
  @Valid
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

  /**
   * The URL is the one place where the inline value wins over the credential rather than the other
   * way round - but only for OAuth credentials, which carry no URL of their own (see {@link
   * RestAuthenticationConfiguration#carriesUrl}) and so have nothing to conflict with. A
   * Basic/Bearer/API-key credential rejects any inline URL outright (see {@link
   * #isUrlOverrideAbsentForHostBoundCredential()}), so if one is bound and reaches this point, the
   * inline value is necessarily blank.
   */
  @Override
  public String getUrl() {
    String inlineUrl = super.getUrl();
    if (inlineUrl != null && !inlineUrl.isBlank()) {
      return inlineUrl;
    }
    return authenticationConfiguration != null ? authenticationConfiguration.url() : null;
  }

  /**
   * A bound Basic/Bearer/API-key credential's secret must never risk being sent to a different host
   * than the one it was created for, so no inline URL is allowed at all once one is bound - simpler
   * and safer than comparing origins. OAuth credentials carry no URL (see {@link
   * RestAuthenticationConfiguration#carriesUrl}), so the inline value is the only source there and
   * is unaffected by this check.
   */
  @AssertTrue(message = "Inline URL override is not allowed once a credential provides the URL")
  @JsonIgnore
  public boolean isUrlOverrideAbsentForHostBoundCredential() {
    if (authenticationConfiguration == null
        || !RestAuthenticationConfiguration.carriesUrl(
            authenticationConfiguration.authentication())) {
      return true;
    }
    String inlineUrl = super.getUrl();
    return inlineUrl == null || inlineUrl.isBlank();
  }
}
