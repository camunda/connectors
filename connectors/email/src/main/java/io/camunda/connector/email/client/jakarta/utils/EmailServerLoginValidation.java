/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.utils;

import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.config.Configuration;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.email.config.Pop3Config;
import io.camunda.connector.email.config.SmtpConfig;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Out-of-band login check shared by every email account credential validator: logs in to a single
 * server and classifies the outcome. Bounded by a timeout, since a host that completes the TCP
 * handshake and then goes silent would otherwise park the request thread indefinitely, given the
 * mail session sets no connect timeout of its own.
 */
public final class EmailServerLoginValidation {

  public static final Duration VALIDATION_TIMEOUT = Duration.ofSeconds(10);

  private static final Logger LOG = LoggerFactory.getLogger(EmailServerLoginValidation.class);
  static final String UNAUTHORIZED_MESSAGE = "The %s server rejected the username or password.";
  static final String GENERIC_MESSAGE = "Could not validate this account against the %s server.";

  private EmailServerLoginValidation() {}

  public static ConfigurationValidationResult login(
      JakartaUtils jakartaUtils, Configuration server, SimpleAuthentication authentication) {
    try {
      Session session = jakartaUtils.createSession(server, authentication, VALIDATION_TIMEOUT);
      switch (server) {
        case SmtpConfig ignored -> {
          try (Transport transport = session.getTransport()) {
            jakartaUtils.connectTransport(transport, authentication);
          }
        }
        case ImapConfig ignored -> openStore(jakartaUtils, session, authentication);
        case Pop3Config ignored -> openStore(jakartaUtils, session, authentication);
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

  private static void openStore(
      JakartaUtils jakartaUtils, Session session, SimpleAuthentication authentication)
      throws Exception {
    try (Store store = session.getStore()) {
      jakartaUtils.connectStore(store, authentication);
    }
  }

  private static String protocolNameOf(Configuration server) {
    return switch (server) {
      case SmtpConfig ignored -> "SMTP";
      case ImapConfig ignored -> "IMAP";
      case Pop3Config ignored -> "POP3";
    };
  }

  private static ConfigurationValidationResult classifyFailure(String protocol, Exception e) {
    return e instanceof AuthenticationFailedException
        ? ConfigurationValidationResult.failure(
            ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE.formatted(protocol))
        : ConfigurationValidationResult.failure(
            ErrorCode.ERROR, GENERIC_MESSAGE.formatted(protocol));
  }
}
