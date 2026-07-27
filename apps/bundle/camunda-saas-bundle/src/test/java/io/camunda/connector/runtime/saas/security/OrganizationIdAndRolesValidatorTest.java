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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

public class OrganizationIdAndRolesValidatorTest {

  private static final String ORGS_CLAIM = "https://camunda.com/orgs";
  private static final String ORGANIZATION_ID = "organizationId";
  private static final List<String> ALLOWED_ROLES_SINGLE = List.of("allowedRoles");
  private static final List<String> ALLOWED_ROLES_MULTIPLE =
      List.of("allowedRoles1", "allowedRoles2");

  @Test
  public void shouldReturnOK_whenOrganizationIdAndSingleRoleAreValid()
      throws JsonProcessingException {
    // given
    var claimContent =
        """
            [
                {
                "id": "organizationId",
                "roles": ["allowedRoles"]
                }
            ]
            """;

    var validator = new OrganizationIdAndRolesValidator(ORGANIZATION_ID, ALLOWED_ROLES_SINGLE);
    Jwt jwt =
        Jwt.withTokenValue("value")
            .header("name", "val")
            .claim(
                ORGS_CLAIM,
                ConnectorsObjectMapperSupplier.getCopy()
                    .readValue(claimContent, new TypeReference<List<Map<String, Object>>>() {}))
            .build();

    // when
    var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  public void shouldReturnOK_whenOrganizationIdAndMultipleRolesAreValid()
      throws JsonProcessingException {
    // given
    var claimContent =
        """
                [
                    {
                    "id": "organizationId",
                    "roles": ["allowedRoles1", "allowedRoles2"]
                    }
                ]
                """;

    var validator = new OrganizationIdAndRolesValidator(ORGANIZATION_ID, ALLOWED_ROLES_MULTIPLE);
    Jwt jwt =
        Jwt.withTokenValue("value")
            .header("name", "val")
            .claim(
                ORGS_CLAIM,
                ConnectorsObjectMapperSupplier.getCopy()
                    .readValue(claimContent, new TypeReference<List<Map<String, Object>>>() {}))
            .build();

    // when
    var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  public void shouldReturnError_whenOrganizationIdIsInvalid() throws JsonProcessingException {
    // given
    var claimContent =
        """
                    [
                        {
                        "id": "invalidOrganizationId",
                        "roles": ["allowedRoles"]
                        }
                    ]
                    """;

    var validator = new OrganizationIdAndRolesValidator(ORGANIZATION_ID, ALLOWED_ROLES_SINGLE);
    Jwt jwt =
        Jwt.withTokenValue("value")
            .header("name", "val")
            .claim(
                ORGS_CLAIM,
                ConnectorsObjectMapperSupplier.getCopy()
                    .readValue(claimContent, new TypeReference<List<Map<String, Object>>>() {}))
            .build();

    // when
    var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors().size()).isEqualTo(1);
    assertThat(new ArrayList<>(result.getErrors()).getFirst().toString())
        .isEqualTo(
            "[invalid_token] The 'https://camunda.com/orgs' claim has no id matching the organization id: [organizationId] or the roles are not in the allowed roles: [allowedRoles]");
  }

  @Test
  public void shouldReturnError_whenRolesAreInvalid() throws JsonProcessingException {
    // given
    var claimContent =
        """
                        [
                            {
                            "id": "organizationId",
                            "roles": ["invalidRole"]
                            }
                        ]
                        """;

    var validator = new OrganizationIdAndRolesValidator(ORGANIZATION_ID, ALLOWED_ROLES_SINGLE);
    Jwt jwt =
        Jwt.withTokenValue("value")
            .header("name", "val")
            .claim(
                ORGS_CLAIM,
                ConnectorsObjectMapperSupplier.getCopy()
                    .readValue(claimContent, new TypeReference<List<Map<String, Object>>>() {}))
            .build();

    // when
    var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors().size()).isEqualTo(1);
    assertThat(new ArrayList<>(result.getErrors()).getFirst().toString())
        .isEqualTo(
            "[invalid_token] The 'https://camunda.com/orgs' claim has no id matching the organization id: [organizationId] or the roles are not in the allowed roles: [allowedRoles]");
  }

  @Test
  public void shouldReturnError_whenOrganizationIdAndRolesAreInvalid()
      throws JsonProcessingException {
    // given
    var claimContent =
        """
                            [
                                {
                                "id": "invalidOrganizationId",
                                "roles": ["invalidRole"]
                                }
                            ]
                            """;

    var validator = new OrganizationIdAndRolesValidator(ORGANIZATION_ID, ALLOWED_ROLES_SINGLE);
    Jwt jwt =
        Jwt.withTokenValue("value")
            .header("name", "val")
            .claim(
                ORGS_CLAIM,
                ConnectorsObjectMapperSupplier.getCopy()
                    .readValue(claimContent, new TypeReference<List<Map<String, Object>>>() {}))
            .build();

    // when
    var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors().size()).isEqualTo(1);
    assertThat(new ArrayList<>(result.getErrors()).getFirst().toString())
        .isEqualTo(
            "[invalid_token] The 'https://camunda.com/orgs' claim has no id matching the organization id: [organizationId] or the roles are not in the allowed roles: [allowedRoles]");
  }

  @Test
  public void shouldReturnError_whenRolesAreNull() throws JsonProcessingException {
    // given
    var claimContent =
        """
                            [
                                {
                                "id": "organizationId",
                                "roles": null
                                }
                            ]
                            """;

    var validator = new OrganizationIdAndRolesValidator(ORGANIZATION_ID, ALLOWED_ROLES_SINGLE);
    Jwt jwt =
        Jwt.withTokenValue("value")
            .header("name", "val")
            .claim(
                ORGS_CLAIM,
                ConnectorsObjectMapperSupplier.getCopy()
                    .readValue(claimContent, new TypeReference<List<Map<String, Object>>>() {}))
            .build();

    // when
    var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors().size()).isEqualTo(1);
    assertThat(new ArrayList<>(result.getErrors()).getFirst().toString())
        .isEqualTo(
            "[invalid_token] The 'https://camunda.com/orgs' claim has no id matching the organization id: [organizationId] or the roles are not in the allowed roles: [allowedRoles]");
  }

  @Test
  public void shouldReturnError_whenRolesAreEmpty() throws JsonProcessingException {
    // given
    var claimContent =
        """
                                [
                                    {
                                    "id": "organizationId",
                                    "roles": []
                                    }
                                ]
                                """;

    var validator = new OrganizationIdAndRolesValidator(ORGANIZATION_ID, ALLOWED_ROLES_SINGLE);
    Jwt jwt =
        Jwt.withTokenValue("value")
            .header("name", "val")
            .claim(
                ORGS_CLAIM,
                ConnectorsObjectMapperSupplier.getCopy()
                    .readValue(claimContent, new TypeReference<List<Map<String, Object>>>() {}))
            .build();

    // when
    var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors().size()).isEqualTo(1);
    assertThat(new ArrayList<>(result.getErrors()).getFirst().toString())
        .isEqualTo(
            "[invalid_token] The 'https://camunda.com/orgs' claim has no id matching the organization id: [organizationId] or the roles are not in the allowed roles: [allowedRoles]");
  }

  @Test
  public void shouldReturnError_whenEmptyClaim() throws JsonProcessingException {
    // given
    var claimContent = "[]";

    var validator = new OrganizationIdAndRolesValidator(ORGANIZATION_ID, ALLOWED_ROLES_SINGLE);
    Jwt jwt =
        Jwt.withTokenValue("value")
            .header("name", "val")
            .claim(
                ORGS_CLAIM,
                ConnectorsObjectMapperSupplier.getCopy()
                    .readValue(claimContent, new TypeReference<List<Map<String, Object>>>() {}))
            .build();

    // when
    var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors().size()).isEqualTo(1);
    assertThat(new ArrayList<>(result.getErrors()).getFirst().toString())
        .isEqualTo(
            "[invalid_token] The 'https://camunda.com/orgs' claim has no id matching the organization id: [organizationId] or the roles are not in the allowed roles: [allowedRoles]");
  }

  @Test
  public void shouldReturnError_whenMissingClaim() {
    // given
    var validator = new OrganizationIdAndRolesValidator(ORGANIZATION_ID, ALLOWED_ROLES_SINGLE);
    Jwt jwt =
        Jwt.withTokenValue("value").claim("otherClaim", "value").header("name", "val").build();

    // when
    var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors().size()).isEqualTo(1);
    assertThat(new ArrayList<>(result.getErrors()).getFirst().toString())
        .isEqualTo("[invalid_token] The required 'https://camunda.com/orgs' claim is missing");
  }
}
