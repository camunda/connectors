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
package io.camunda.connector.runtime.bundle.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Mirrors {@code camunda-saas-bundle}'s validator of the same name; this bundle cannot depend on
 * that module (the dependency runs the other way), so this is a local copy rather than a shared
 * one.
 */
class AudienceValidator implements OAuth2TokenValidator<Jwt> {
  private final String audience;

  AudienceValidator(String audience) {
    this.audience = audience;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    // aud is optional in a JWT, and Jwt#getAudience is null when the claim is absent rather than
    // an empty list — a token minted without it must be rejected, not blow up mid-filter.
    var tokenAudience = jwt.getAudience();
    if (tokenAudience != null && tokenAudience.contains(audience)) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "The required audience is missing", null));
  }
}
