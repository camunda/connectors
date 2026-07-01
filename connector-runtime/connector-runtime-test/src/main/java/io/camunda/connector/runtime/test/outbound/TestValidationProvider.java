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
package io.camunda.connector.runtime.test.outbound;

import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.hostvalidator.VerifiedHostValidator;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

public class TestValidationProvider implements ValidationProvider {

  private final ValidationProvider delegate;

  public TestValidationProvider(VerifiedHostValidator verifiedHostValidator) {
    /*
     Basic implementation that is able to discover the {@link VerifiedHostValidator}
    */
    ConstraintValidatorFactory factory =
        new ConstraintValidatorFactory() {
          @Override
          public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            if (key.equals(VerifiedHostValidator.class)) {
              return key.cast(verifiedHostValidator);
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

    ValidatorFactory validatorFactory =
        Validation.byDefaultProvider()
            .configure()
            .constraintValidatorFactory(factory)
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();

    this.delegate = new DefaultValidationProvider(validatorFactory);
  }

  public TestValidationProvider() {
    this(
        new VerifiedHostValidator(
            new VerifiedHostValidator.Config(true, List.of(), List.of(), true, true)));
  }

  @Override
  public void validate(Object objectToValidate) {
    delegate.validate(objectToValidate);
  }
}
