/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

/**
 * The shape a bound credential has at runtime for a connector that talks to one known service
 * through the shared {@code io.camunda:http-json:1} task type (Asana, OpenAI, HubSpot, ...). Unlike
 * {@link RestAuthenticationConfiguration} it carries no URL: the element template already knows the
 * endpoint, so the credential holds only how to authenticate.
 *
 * <p>This is a wire type, not a credential definition. Each such connector declares its own
 * credential type in the {@code configurationTemplates} of its element template, with its own id
 * and name, so that Modeler offers a connector only its own credentials. Those credential types pin
 * the authentication mechanism with hidden constants (e.g. {@code authentication.type = bearer}),
 * and the values they save deserialize into this one record.
 */
public record ServiceAuthenticationConfiguration(@Valid Authentication authentication) {

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
}
