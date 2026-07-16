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
package io.camunda.connector.validation;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import jakarta.validation.constraints.AssertTrue;
import org.junit.jupiter.api.Test;

/**
 * Verifies the "required via getter, not field" pattern that connector request models use when a
 * value may come from more than one source (e.g. an inline field OR a bound credential): the field
 * itself is left nullable, and requiredness is enforced by a getter-based {@link AssertTrue}
 * constraint that reflects the resolved value. This mirrors {@code
 * AwsBaseRequest#isAuthenticationPresent()} and proves the connector validation package ({@link
 * DefaultValidationProvider}) actually enforces such constraints — including when the getter is
 * derived, when it is overridden in a subclass, and when it also carries {@code @JsonIgnore}.
 */
class GetterConstraintValidationTest {

  private static final ValidationProvider VALIDATION = new DefaultValidationProvider();

  /**
   * Base request: the inline field carries NO {@code @NotNull}; requiredness lives on the getter,
   * which resolves the value from an alternative source (credential) first, then the inline field —
   * exactly like {@code AwsBaseRequest#getAuthentication()}.
   */
  static class CredentialAwareRequest {
    private String authentication; // intentionally NOT @NotNull
    private String credential; // alternative source, e.g. a bound credential

    public String getAuthentication() {
      return credential != null ? credential : authentication;
    }

    public void setAuthentication(String authentication) {
      this.authentication = authentication;
    }

    public void setCredential(String credential) {
      this.credential = credential;
    }

    @AssertTrue(message = "Authentication is required")
    @JsonIgnore
    public boolean isAuthenticationPresent() {
      return getAuthentication() != null;
    }
  }

  /**
   * Subclass that supplies the value by OVERRIDING the getter (the original {@code S3Request}
   * approach, before the chooser was hoisted into the shared base). The inherited getter-based
   * constraint must observe the overridden method via polymorphic dispatch.
   */
  static class OverridingRequest extends CredentialAwareRequest {
    private String boundAuthentication;

    public void setBoundAuthentication(String boundAuthentication) {
      this.boundAuthentication = boundAuthentication;
    }

    @Override
    public String getAuthentication() {
      return boundAuthentication != null ? boundAuthentication : super.getAuthentication();
    }
  }

  @Test
  void failsWhenNeitherInlineNorAlternativeIsPresent() {
    var request = new CredentialAwareRequest();

    assertThatExceptionOfType(ConnectorInputException.class)
        .isThrownBy(() -> VALIDATION.validate(request))
        .withMessageContaining("authenticationPresent")
        .withMessageContaining("Authentication is required");
  }

  @Test
  void passesWhenInlineFieldIsPresent() {
    var request = new CredentialAwareRequest();
    request.setAuthentication("inline-value");

    assertThatNoException().isThrownBy(() -> VALIDATION.validate(request));
  }

  @Test
  void passesWhenOnlyAlternativeSourceIsPresent() {
    // The inline field stays null; the getter resolves requiredness from the credential.
    var request = new CredentialAwareRequest();
    request.setCredential("credential-value");

    assertThatNoException().isThrownBy(() -> VALIDATION.validate(request));
  }

  @Test
  void honorsOverriddenGetterInSubclass() {
    // Neither base field is set; the subclass supplies the value by overriding getAuthentication().
    var request = new OverridingRequest();
    request.setBoundAuthentication("subclass-value");

    assertThatNoException().isThrownBy(() -> VALIDATION.validate(request));
  }

  @Test
  void failsForSubclassWhenNoSourceIsPresent() {
    assertThatExceptionOfType(ConnectorInputException.class)
        .isThrownBy(() -> VALIDATION.validate(new OverridingRequest()))
        .withMessageContaining("authenticationPresent");
  }
}
