/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.email.model.output;

import com.microsoft.graph.models.Recipient;
import java.util.List;
import java.util.Optional;

// Move mapping external to record
// Clean core
public record EmailAddress(String name, String address) {
  public EmailAddress(Recipient recipient) {
    String name = null;
    String address = null;
    if (recipient != null && recipient.getEmailAddress() != null) {
      name = recipient.getEmailAddress().getName();
      address = recipient.getEmailAddress().getAddress();
    }
    if (name == null) {
      name = "";
    }
    if (address == null) {
      address = "";
    }
    this(name, address);
  }

  public static List<EmailAddress> transformList(List<Recipient> recipients) {
    return Optional.ofNullable(recipients).stream()
        .flatMap(e -> e.stream().map(EmailAddress::new))
        .toList();
  }
}
