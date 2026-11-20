/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.hostvalidator.VerifiedHost;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The shape a bound credential has at runtime for a connector that talks to one known service
 * through the shared {@code io.camunda:http-json:1} task type (Asana, OpenAI, ServiceNow, ...).
 * Unlike {@link RestAuthenticationConfiguration} it never requires a URL: the element template
 * already knows the endpoint, or the part of it that the credential does not hold.
 *
 * <p>This is a wire type, not a credential definition. Each such connector declares its own
 * credential type in the {@code configurationTemplates} of its element template, with its own id
 * and name, so that Modeler offers a connector only its own credentials. Those credential types pin
 * the authentication mechanism with hidden constants (e.g. {@code authentication.type = bearer}),
 * and the values they save deserialize into this one record.
 *
 * <h2>Base URL and parameters</h2>
 *
 * Some services live at an address the admin knows and the builder does not (a ServiceNow instance,
 * a Databricks host, a Camunda cluster). The credential then carries a {@code baseUrl}, and the
 * element template sends only the {@code relativeUrl} of the operation. {@code HttpJsonRequest}
 * joins the two.
 *
 * <p>The admin should enter only what cannot be known in advance, such as the instance name. The
 * credential type therefore holds the rest as hidden constants that refer to the entered values
 * through {@code ${name}} placeholders, for example {@code baseUrl =
 * https://${instance}.service-now.com} with {@code parameters.instance} as the visible field. The
 * placeholders are replaced here, in {@code baseUrl}, the OAuth token endpoint and the Basic
 * username, so the rest of the runtime only sees final values. Nothing else is expanded, secrets in
 * particular. The template string is read once from left to right, so a parameter value that itself
 * looks like a placeholder is kept as text and never expanded.
 */
public record ServiceAuthenticationConfiguration(
    @Valid Authentication authentication,
    @Pattern(
            regexp = RestAuthenticationConfiguration.URL_PATTERN,
            message = "Must be a http(s) URL")
        @VerifiedHost(isUri = true)
        String baseUrl,
    Map<String, String> parameters) {

  public ServiceAuthenticationConfiguration {
    // A parameter without a value is dropped, so that a placeholder naming it is rejected
    // as unresolved rather than failing here or being replaced by nothing.
    parameters =
        parameters == null
            ? Map.of()
            : parameters.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    authentication = expand(authentication, parameters);
    baseUrl =
        baseUrl == null || baseUrl.isBlank()
            ? null
            : expand(baseUrl, parameters, ServiceAuthenticationConfiguration::urlPart)
                .strip()
                .replaceAll("/+$", "");
  }

  public ServiceAuthenticationConfiguration(Authentication authentication) {
    this(authentication, null, null);
  }

  /**
   * The credential editor cannot produce {@link NoAuthentication}, so rejecting it here only closes
   * the hand-edited and hand-constructed paths, and covers a credential that carries no
   * authentication at all.
   */
  @AssertTrue(message = "A credential must specify an authentication mechanism other than 'None'")
  @JsonIgnore
  public boolean isAuthenticationSupported() {
    return authentication != null && !(authentication instanceof NoAuthentication);
  }

  /**
   * A value that goes into a URL is cleaned first, so that a host typed with a trailing slash or a
   * stray space does not leave a double slash or a space in the result. Values that must stay
   * exact, such as a username, are used as typed.
   */
  private static String urlPart(String value) {
    return value.strip().replaceAll("/+$", "");
  }

  private static String expand(
      String value, Map<String, String> parameters, UnaryOperator<String> clean) {
    if (value == null) {
      return null;
    }
    var result = new StringBuilder();
    int copied = 0;
    for (int start = value.indexOf("${"); start >= 0; start = value.indexOf("${", copied)) {
      int end = value.indexOf('}', start);
      if (end < 0) {
        break;
      }
      var name = value.substring(start + 2, end);
      var replacement = parameters.get(name) == null ? null : clean.apply(parameters.get(name));
      if (replacement == null || replacement.isEmpty()) {
        throw new IllegalArgumentException(
            "The credential refers to parameter '" + name + "', which has no value");
      }
      result.append(value, copied, start).append(replacement);
      copied = end + 1;
    }
    return result.append(value, copied, value.length()).toString();
  }

  private static Authentication expand(Authentication authentication, Map<String, String> p) {
    if (authentication instanceof BasicAuthentication basic) {
      return new BasicAuthentication(
          expand(basic.username(), p, UnaryOperator.identity()), basic.password());
    }
    if (authentication instanceof OAuthAuthentication oauth) {
      return new OAuthAuthentication(
          expand(oauth.oauthTokenEndpoint(), p, ServiceAuthenticationConfiguration::urlPart),
          oauth.clientId(),
          oauth.clientSecret(),
          oauth.audience(),
          oauth.clientAuthentication(),
          oauth.scopes());
    }
    return authentication;
  }
}
