/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.outbound;

import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.config.Configuration;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.email.config.Pop3Config;
import io.camunda.connector.email.config.SmtpConfig;
import io.camunda.connector.email.outbound.model.EmailAccountConfiguration;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates a stored {@link EmailAccountConfiguration} by logging in to every server it carries.
 * The account is usable only if all of them accept it, so the first failure decides the verdict.
 */
public class EmailAccountValidator implements ConfigurationValidator<EmailAccountConfiguration> {

  private static final Logger LOG = LoggerFactory.getLogger(EmailAccountValidator.class);

  private static final Duration VALIDATION_TIMEOUT = Duration.ofSeconds(10);

  static final String MISSING_SERVER_MESSAGE =
      "Configure at least one of the SMTP, IMAP or POP3 servers, so this email account can be validated.";
  static final String UNAUTHORIZED_MESSAGE = "The %s server rejected the username or password.";
  static final String GENERIC_MESSAGE = "Could not validate this account against the %s server.";

  private final JakartaUtils jakartaUtils = new JakartaUtils();

  @Override
  public ConfigurationValidationResult validate(EmailAccountConfiguration configuration) {
    List<Configuration> servers =
        Stream.of(
                configuration.toSmtpConfig(),
                configuration.toImapConfig(),
                configuration.toPop3Config())
            .filter(Objects::nonNull)
            .map(Configuration.class::cast)
            .toList();
    if (servers.isEmpty()) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_SERVER_MESSAGE);
    }
    SimpleAuthentication authentication = configuration.toAuthentication();
    for (Configuration server : servers) {
      ConfigurationValidationResult result = login(server, authentication);
      if (result.status() != Status.SUCCESS) {
        return result;
      }
    }
    return ConfigurationValidationResult.success();
  }

  private ConfigurationValidationResult login(
      Configuration server, SimpleAuthentication authentication) {
    try {
      Session session = jakartaUtils.createSession(server, authentication, VALIDATION_TIMEOUT);
      switch (server) {
        case SmtpConfig ignored -> {
          try (Transport transport = session.getTransport()) {
            jakartaUtils.connectTransport(transport, authentication);
          }
        }
        case ImapConfig ignored -> openStore(session, authentication);
        case Pop3Config ignored -> openStore(session, authentication);
      }
      return ConfigurationValidationResult.success();
    } catch (Exception e) {
      LOG.debug(
          "Email account credential validation failed for {} ({})",
          protocolNameOf(server),
          e.getClass().getName());
      return classifyFailure(protocolNameOf(server), e);
    }
  }

  private void openStore(Session session, SimpleAuthentication authentication) throws Exception {
    try (Store store = session.getStore()) {
      jakartaUtils.connectStore(store, authentication);
    }
  }

  static String protocolNameOf(Configuration server) {
    return switch (server) {
      case SmtpConfig ignored -> "SMTP";
      case ImapConfig ignored -> "IMAP";
      case Pop3Config ignored -> "POP3";
    };
  }

  static ConfigurationValidationResult classifyFailure(String protocol, Exception e) {
    return e instanceof AuthenticationFailedException
        ? ConfigurationValidationResult.failure(
            ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE.formatted(protocol))
        : ConfigurationValidationResult.failure(
            ErrorCode.ERROR, GENERIC_MESSAGE.formatted(protocol));
  }
}
