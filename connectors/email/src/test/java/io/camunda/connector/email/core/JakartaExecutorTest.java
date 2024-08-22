/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.core;

import static org.junit.jupiter.api.Assertions.*;

import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.model.EmailRequest;
import io.camunda.connector.email.protocols.Smtp;
import io.camunda.connector.email.protocols.actions.SmtpSendEmail;
import io.camunda.connector.email.protocols.config.SmtpConfig;
import org.junit.jupiter.api.Test;

class JakartaExecutorTest {

  @Test
  void execute() {
    var emailRequest = new EmailRequest();
    emailRequest.setAuthentication(new SimpleAuthentication());
    var smtp = new Smtp();
    var config = new SmtpConfig();
    config.setSmtpAuth(true);
    config.setSmtpTLS(true);
    config.setSmtpHost("df");
    config.setSmtpPort(34);
    smtp.setSmtpConfig(config);
    var smtpAction = new SmtpSendEmail();
    smtpAction.setTo("d");
    smtpAction.setSubject("d");
    smtpAction.setBody("d");
    smtp.setSmtpAction(smtpAction);
    emailRequest.setData(smtp);
    JakartaExecutor.execute(emailRequest);
  }
}
