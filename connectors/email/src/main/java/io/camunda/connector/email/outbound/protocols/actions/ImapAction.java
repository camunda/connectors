/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateDiscriminatorProperty(
    label = "IMAP action",
    group = "imapAction",
    name = "imapAction",
    defaultValue = "listEmailImap")
@TemplateSubType(id = "imapAction", label = "IMAP Action")
public sealed interface ImapAction extends Action
    permits ImapDeleteEmail, ImapListEmails, ImapMoveEmails, ImapReadEmail, ImapSearchEmails {}
