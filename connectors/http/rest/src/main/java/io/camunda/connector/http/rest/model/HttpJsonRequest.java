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
import io.camunda.connector.http.base.model.auth.RestAuthenticationConfiguration;
import io.camunda.connector.http.base.model.auth.ServiceAuthenticationConfiguration;
import jakarta.validation.Valid;

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
   * The credential bound by the templates of connectors that wrap {@code http-json} for one known
   * service. It is not offered by the generic REST template, so the generator must not emit a
   * chooser for it; those connectors' own templates declare the chooser and the credential type.
   * The explicit {@code type} makes the generator treat the field as a leaf, which is what lets
   * {@code ignore} apply instead of the credential's fields being expanded.
   */
  @TemplateProperty(ignore = true, type = PropertyType.Hidden)
  @Valid
  private ServiceAuthenticationConfiguration serviceAuthenticationConfiguration;

  public ServiceAuthenticationConfiguration getServiceAuthenticationConfiguration() {
    return serviceAuthenticationConfiguration;
  }

  public void setServiceAuthenticationConfiguration(
      ServiceAuthenticationConfiguration serviceAuthenticationConfiguration) {
    this.serviceAuthenticationConfiguration = serviceAuthenticationConfiguration;
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
    if (serviceAuthenticationConfiguration != null) {
      return serviceAuthenticationConfiguration.authentication();
    }
    return super.getAuthentication();
  }

  /**
   * The part of the URL after the base URL held by a bound service credential. It starts with a
   * slash, and the credential's base URL has its trailing slashes removed, so the two are simply
   * concatenated. Templates of connectors whose credential carries the base (a ServiceNow instance,
   * a Databricks host, ...) send this instead of an absolute {@code url} once a credential is
   * chosen. Like the credential itself, it is not offered by the generic REST template.
   */
  @TemplateProperty(ignore = true)
  private String relativeUrl;

  public String getRelativeUrl() {
    return relativeUrl;
  }

  public void setRelativeUrl(String relativeUrl) {
    this.relativeUrl = relativeUrl == null || relativeUrl.isBlank() ? null : relativeUrl;
  }

  /**
   * The URL is the one place where the inline value wins over the credential rather than the other
   * way round: the credential's URL is the endpoint it is bound to by default, but a process author
   * who sets an inline URL on the task means it. Binding the credential and pointing the task
   * elsewhere are both decisions of the same author, so no origin check constrains the override -
   * for an OAuth credential, which need not carry a URL at all, the inline value is often the only
   * source.
   */
  @Override
  public String getUrl() {
    String inlineUrl = super.getUrl();
    if (inlineUrl != null && !inlineUrl.isBlank()) {
      return inlineUrl;
    }
    if (serviceAuthenticationConfiguration != null
        && serviceAuthenticationConfiguration.baseUrl() != null
        && relativeUrl != null) {
      return serviceAuthenticationConfiguration.baseUrl() + relativeUrl;
    }
    return authenticationConfiguration != null ? authenticationConfiguration.url() : null;
  }
}
