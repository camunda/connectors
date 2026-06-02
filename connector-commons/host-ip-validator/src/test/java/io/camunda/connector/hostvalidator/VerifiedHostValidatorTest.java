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
package io.camunda.connector.hostvalidator;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

class VerifiedHostValidatorTest {

  /** Bean validation target with a marker-annotated host string. */
  private record Target(@VerifiedHost String host) {}

  /** Bean validation target with a marker-annotated url string */
  private record TargetWithURL(@VerifiedHost(isUri = true) String url) {}

  /**
   * Builds a {@link Validator} whose {@link ConstraintValidatorFactory} hands out the provided
   * stateful {@link VerifiedHostValidator}. This is the wiring described in the Baeldung guide on
   * stateful bean validation, scaled down to a test fixture.
   */
  private static Validator validatorWith(VerifiedHostValidator stateful) {
    ConstraintValidatorFactory factory =
        new ConstraintValidatorFactory() {
          @Override
          public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            if (key.equals(VerifiedHostValidator.class)) {
              return key.cast(stateful);
            }
            try {
              return key.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
              throw new IllegalStateException(e);
            }
          }

          @Override
          public void releaseInstance(ConstraintValidator<?, ?> instance) {}
        };
    return Validation.byDefaultProvider()
        .configure()
        .messageInterpolator(new ParameterMessageInterpolator())
        .constraintValidatorFactory(factory)
        .buildValidatorFactory()
        .getValidator();
  }

  @Test
  void publicHostnamePasses() {
    Validator v = validatorWith(new VerifiedHostValidator());
    Set<ConstraintViolation<Target>> violations = v.validate(new Target("8.8.8.8"));
    assertThat(violations).isEmpty();
  }

  @Test
  void loopbackIsRejected() {
    Validator v = validatorWith(new VerifiedHostValidator());
    Set<ConstraintViolation<Target>> violations = v.validate(new Target("127.0.0.1"));
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getMessage()).contains("DENY_NOT_GLOBAL_UNICAST");
  }

  @Test
  void privateRangeIsRejectedByDefault() {
    Validator v = validatorWith(new VerifiedHostValidator());
    assertThat(v.validate(new Target("10.0.0.1"))).hasSize(1);
  }

  @Test
  void configCanAllowPrivateRanges() {
    VerifiedHostValidator.Config config =
        new VerifiedHostValidator.Config(true, List.of(), List.of(), true, false);
    Validator v = validatorWith(new VerifiedHostValidator(config));
    assertThat(v.validate(new Target("10.0.0.1"))).isEmpty();
  }

  @Test
  void configCanAllowLoopbackViaAllowRange() {
    VerifiedHostValidator.Config config =
        new VerifiedHostValidator.Config(
            true, List.of(CidrRange.parse("127.0.0.0/8")), List.of(), false, false);
    Validator v = validatorWith(new VerifiedHostValidator(config));
    assertThat(v.validate(new Target("127.0.0.1"))).isEmpty();
  }

  @Test
  void configDenyRangeRejectsOtherwiseAllowedHost() {
    VerifiedHostValidator.Config config =
        new VerifiedHostValidator.Config(
            true, List.of(), List.of(CidrRange.parse("8.8.8.0/24")), false, false);
    Validator v = validatorWith(new VerifiedHostValidator(config));
    Set<ConstraintViolation<Target>> violations = v.validate(new Target("8.8.8.8"));
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getMessage()).contains("DENY_USER_CONFIGURED");
  }

  @Test
  void unresolvableHostnameProducesViolation() {
    Validator v = validatorWith(new VerifiedHostValidator());
    Set<ConstraintViolation<Target>> violations =
        v.validate(new Target("no-such-host.invalid.camunda.test"));
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getMessage()).contains("Could not resolve");
  }

  @Test
  void nullAndBlankAreTreatedAsValid() {
    Validator v = validatorWith(new VerifiedHostValidator());
    assertThat(v.validate(new Target(null))).isEmpty();
    assertThat(v.validate(new Target(""))).isEmpty();
    assertThat(v.validate(new Target("   "))).isEmpty();
  }

  @Test
  void defaultConstructorAppliesDefaultConfig() {
    VerifiedHostValidator validator = new VerifiedHostValidator();
    assertThat(validator.config()).isEqualTo(VerifiedHostValidator.Config.defaults());
  }

  @Test
  void configRejectsNull() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new VerifiedHostValidator(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void testWithUrl() {
    VerifiedHostValidator.Config config =
        new VerifiedHostValidator.Config(true, List.of(), List.of(), false, false);
    Validator v = validatorWith(new VerifiedHostValidator(config));
    assertThat(v.validate(new TargetWithURL("http://example.com"))).isEmpty();
  }
  
}
