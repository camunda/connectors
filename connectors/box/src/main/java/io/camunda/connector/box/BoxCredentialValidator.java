/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import com.box.sdkgen.box.errors.BoxAPIError;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.box.model.BoxCredentialConfiguration;
import io.camunda.connector.box.model.BoxRequest.Authentication;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates a stored {@link BoxCredentialConfiguration} by fetching the authenticated user ({@code
 * GET /users/me}), which first exchanges the credential for an access token.
 */
public class BoxCredentialValidator implements ConfigurationValidator<BoxCredentialConfiguration> {

  private static final Logger LOG = LoggerFactory.getLogger(BoxCredentialValidator.class);

  static final String MISSING_AUTH_MESSAGE = "A Box authentication must be provided.";
  static final String UNAUTHORIZED_MESSAGE = "Box rejected the credential (unauthorized).";
  static final String GENERIC_MESSAGE = "The Box credential could not be validated.";

  // the token endpoint answers 400 for an unknown client, a wrong secret or subject
  private static final Set<Integer> REJECTION_STATUSES = Set.of(400, 401, 403);

  @FunctionalInterface
  interface CredentialCheck {
    void run(Authentication authentication);
  }

  private final CredentialCheck credentialCheck;

  public BoxCredentialValidator() {
    this(
        authentication ->
            BoxOperations.connectToApi(authentication, BoxOperations.SESSION).users.getUserMe());
  }

  BoxCredentialValidator(CredentialCheck credentialCheck) {
    this.credentialCheck = credentialCheck;
  }

  @Override
  public ConfigurationValidationResult validate(BoxCredentialConfiguration configuration) {
    if (!(configuration.authentication() instanceof Authentication authentication)) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_AUTH_MESSAGE);
    }
    try {
      credentialCheck.run(authentication);
      return ConfigurationValidationResult.success();
    } catch (RuntimeException e) {
      if (isRejectedByBox(e)) {
        LOG.debug("Box rejected the credential ({})", e.getClass().getName());
        return ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
      }
      LOG.debug("Box credential validation failed ({})", e.getClass().getName());
      return ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
    }
  }

  private static boolean isRejectedByBox(Throwable throwable) {
    for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
      if (cause instanceof BoxAPIError error
          && error.getResponseInfo() != null
          && REJECTION_STATUSES.contains(error.getResponseInfo().getStatusCode())) {
        return true;
      }
    }
    return false;
  }
}
