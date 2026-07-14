/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateSubType(
    id = "searchEmailsPop3",
    label = "Search emails",
    description = "Search for emails in a POP3 mailbox using criteria",
    keywords = {
      "search emails",
      "pop3 search",
      "find emails",
      "filter messages",
      "query inbox",
      "look up emails"
    })
public record Pop3SearchEmails(
    @TemplateProperty(
            label = "Search criteria",
            group = "searchEmailsPop3",
            id = "searchStringEmailPop3",
            tooltip =
                "Define the search criteria using supported keywords and syntax to filter emails. See the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/email/\">Email connector search syntax</a>.",
            type = TemplateProperty.PropertyType.Text,
            feel = FeelMode.required,
            optional = true,
            binding = @TemplateProperty.PropertyBinding(name = "data.pop3Action.criteria"))
        Object criteria)
    implements Pop3Action {}
