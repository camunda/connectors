/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.output;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EmailMessageTest {

  private EmailMessage baseMessage(List<EmailAttachmentMetadata> metadata) {
    return new EmailMessage(
        "id",
        "conversation",
        new EmailAddress("Sender", "sender@example.com"),
        List.of(),
        List.of(),
        List.of(),
        "subject",
        "body",
        "text",
        OffsetDateTime.parse("2025-01-15T10:30:00Z"),
        List.of(),
        metadata);
  }

  @Test
  void copyWithDocuments_preservesAttachmentMetadata() {
    var pdf = new EmailAttachmentMetadata("att-1", "invoice.pdf", "application/pdf", 1024L, false);
    var original = baseMessage(List.of(pdf));
    var downloaded = Mockito.mock(Document.class);

    var copy = new EmailMessage(original, List.of(downloaded));

    assertThat(copy.attachments()).containsExactly(downloaded);
    assertThat(copy.attachmentMetadata()).containsExactly(pdf);
  }

  @Test
  void getSelect_includesHasAttachments() {
    assertThat(EmailMessage.getSelect()).contains("hasAttachments");
  }
}
