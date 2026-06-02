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

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.hibernate.validator.internal.engine.constraintvalidation.ConstraintValidatorContextImpl;
import org.hibernate.validator.internal.metadata.descriptor.ConstraintDescriptorImpl;

/**
 * Stateful Jakarta Bean Validation validator backing {@link VerifiedHost}. Holds its own
 * configuration (allow/deny CIDR ranges, private-range policy) and delegates the actual hostname
 * resolution and classification to {@link HostIpValidator}.
 *
 * <p>Because this validator carries state, the default constraint validator factory's no-arg
 * instantiation is not suitable when non-default configuration is required. To inject configuration
 * — e.g. via Spring — register this class as a bean and configure a stateful {@link
 * jakarta.validation.ConstraintValidatorFactory} (for example {@code
 * SpringConstraintValidatorFactory}) on the {@link jakarta.validation.ValidatorFactory}. See <a
 * href="https://www.baeldung.com/spring-custom-stateful-bean-validation">the Baeldung guide on
 * stateful bean validation</a> for the wiring pattern.
 *
 * <p>A {@code null} or blank value is treated as valid — combine with {@code @NotBlank} when the
 * host is required.
 */
public class VerifiedHostValidator implements ConstraintValidator<VerifiedHost, String> {

  /**
   * Configuration container. Defaults are conservative: no allow/deny ranges configured and private
   * ranges are denied. Pass an instance to the {@link
   * VerifiedHostValidator#VerifiedHostValidator(Config)} constructor.
   */
  public record Config(
      boolean enabled,
      List<CidrRange> allowRanges,
      List<CidrRange> denyRanges,
      boolean unsafeAllowPrivateRanges,
      boolean allowLoopback) {

    public Config {
      allowRanges = allowRanges == null ? List.of() : List.copyOf(allowRanges);
      denyRanges = denyRanges == null ? List.of() : List.copyOf(denyRanges);
    }

    public static Config defaults() {
      return new Config(true, List.of(), List.of(), false, false);
    }
  }

  private final Config config;

  /** Convenience constructor that applies the default configuration. */
  public VerifiedHostValidator() {
    this(Config.defaults());
  }

  public VerifiedHostValidator(Config config) {
    this.config = Objects.requireNonNull(config, "config must not be null");
  }

  public Config config() {
    return config;
  }

  @Override
  public boolean isValid(String host, ConstraintValidatorContext context) {
    if (!config.enabled) {
      return true;
    }

    if (host != null && !host.isBlank()) {
      VerifiedHost verifiedHost = getAnnotation(context);
      if (verifiedHost.isUri()) {
        host = getHostFromUri(host);
      }
    }

    if (host == null || host.isBlank()) {
      return true;
    }
    try {
      HostIpValidator.validate(
          host,
          config.allowRanges(),
          config.denyRanges(),
          config.unsafeAllowPrivateRanges(),
          config.allowLoopback());
      return true;
    } catch (HostDeniedException e) {
      replaceViolationMessage(context, e.getMessage());
      return false;
    } catch (UnknownHostException e) {
      replaceViolationMessage(context, "Could not resolve host '" + host + "'");
      return false;
    }
  }

  private static VerifiedHost getAnnotation(ConstraintValidatorContext context) {
    ConstraintValidatorContextImpl unwrappedContext =
        (ConstraintValidatorContextImpl) context.unwrap(HibernateConstraintValidatorContext.class);
    ConstraintDescriptorImpl unwrappedConstraintDescriptor =
        (ConstraintDescriptorImpl) unwrappedContext.getConstraintDescriptor();
    return (VerifiedHost) unwrappedConstraintDescriptor.getAnnotation();
  }

  private static String getHostFromUri(String uri) {
    try {
      var parsedUri = URI.create(uri);
      if (parsedUri.getHost() != null) {
        return parsedUri.getHost();
      } else return uri;
    } catch (Exception ignored) {
      return uri;
    }
  }

  private static void replaceViolationMessage(ConstraintValidatorContext context, String message) {
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
  }
}
