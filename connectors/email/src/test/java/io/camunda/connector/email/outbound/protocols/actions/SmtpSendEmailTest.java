/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SmtpSendEmailTest {

  @Test
  void isEmailMessageValidPlainTextWithBody() {
    SmtpSendEmail smtpSendEmail = mockSmtpSendEmail(ContentType.PLAIN, "text", null);
    assertTrue(smtpSendEmail.isEmailMessageValid());
  }

  @Test
  void isEmailMessageNotValidPlainTextWithoutBody() {
    SmtpSendEmail smtpSendEmail = mockSmtpSendEmail(ContentType.PLAIN, null, null);
    assertFalse(smtpSendEmail.isEmailMessageValid());
  }

  @Test
  void isEmailMessageValidHtmlTextWithBody() {
    SmtpSendEmail smtpSendEmail = mockSmtpSendEmail(ContentType.HTML, null, "text");
    assertTrue(smtpSendEmail.isEmailMessageValid());
  }

  @Test
  void isEmailMessageNotValidHtmlWithoutBody() {
    SmtpSendEmail smtpSendEmail = mockSmtpSendEmail(ContentType.HTML, null, null);
    assertFalse(smtpSendEmail.isEmailMessageValid());
  }

  @Test
  void isEmailMessageValidMultiWithBody() {
    SmtpSendEmail smtpSendEmail = mockSmtpSendEmail(ContentType.MULTIPART, "text", "text2");
    assertTrue(smtpSendEmail.isEmailMessageValid());
  }

  @Test
  void isEmailMessageNotValidHtmlTextWithoutBothBodies() {
    SmtpSendEmail smtpSendEmail = mockSmtpSendEmail(ContentType.MULTIPART, null, null);
    assertFalse(smtpSendEmail.isEmailMessageValid());
  }

  @Test
  void isEmailMessageNotValidMultiWithoutHtmlBody() {
    SmtpSendEmail smtpSendEmail = mockSmtpSendEmail(ContentType.MULTIPART, "text", null);
    assertFalse(smtpSendEmail.isEmailMessageValid());
  }

  @Test
  void isEmailMessageNotValidMultiWithoutBody() {
    SmtpSendEmail smtpSendEmail = mockSmtpSendEmail(ContentType.MULTIPART, null, "text");
    assertFalse(smtpSendEmail.isEmailMessageValid());
  }

  private SmtpSendEmail mockSmtpSendEmail(ContentType contentType, String body, String htmlBody) {
    return new SmtpSendEmail("", "", "", "", null, "", contentType, body, htmlBody);
  }
}
