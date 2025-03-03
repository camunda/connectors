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
package io.camunda.connector.runtime.saas.security;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class OrganizationIdAndRolesValidator implements OAuth2TokenValidator<Jwt> {
  private final String organizationId;
  private static final String ORGS_CLAIM = "https://camunda.com/orgs";
  private static final String ORGS_CLAIM_ID_KEY = "id";
  private final List<String> allowedRoles;

  OrganizationIdAndRolesValidator(String organizationId, String allowedRoles) {
    this.organizationId = organizationId;
    this.allowedRoles = List.of(allowedRoles.split(","));
  }

  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    OAuth2Error error =
        new OAuth2Error(
            "invalid_token", "The required 'https://camunda.com/orgs' claim is missing", null);
    List<Map<String, ?>> orgs = jwt.getClaim(ORGS_CLAIM);
    if (orgs == null) {
      return OAuth2TokenValidatorResult.failure(error);
    }

    var hasOrganizationIdAndRoles =
        orgs.stream()
            .filter(org -> organizationId.equals(org.get(ORGS_CLAIM_ID_KEY)))
            .map(org -> org.get("roles"))
            .filter(Objects::nonNull)
            .flatMap(roles -> ((List<String>) roles).stream())
            .anyMatch(allowedRoles::contains);

    error =
        new OAuth2Error(
            "invalid_token",
            "The 'https://camunda.com/orgs' claim has no id matching the organization id: ["
                + organizationId
                + "] or the roles are not in the allowed roles: ["
                + allowedRoles
                + "]",
            null);

    return hasOrganizationIdAndRoles
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(error);
  }
}
