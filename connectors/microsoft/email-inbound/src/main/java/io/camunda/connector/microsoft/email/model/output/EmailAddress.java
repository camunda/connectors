/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.output;

import com.microsoft.graph.models.Recipient;
import java.util.List;
import java.util.Optional;

// Move mapping external to record
// Clean core
public record EmailAddress(String name, String address) {
  public EmailAddress(Recipient recipient) {
    this(
        recipient != null
                && recipient.getEmailAddress() != null
                && recipient.getEmailAddress().getName() != null
            ? recipient.getEmailAddress().getName()
            : "",
        recipient != null
                && recipient.getEmailAddress() != null
                && recipient.getEmailAddress().getAddress() != null
            ? recipient.getEmailAddress().getAddress()
            : "");
  }

  public static List<EmailAddress> transformList(List<Recipient> recipients) {
    return Optional.ofNullable(recipients).stream()
        .flatMap(e -> e.stream().map(EmailAddress::new))
        .toList();
  }
}
