/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GraphApiMapperTest {

  private static Recipient graphRecipient(String name, String address) {
    var emailAddress = new EmailAddress();
    emailAddress.setName(name);
    emailAddress.setAddress(address);
    var recipient = new Recipient();
    recipient.setEmailAddress(emailAddress);
    return recipient;
  }

  @Test
  void toEmailMessage_mapsCommonFieldsAndKeepsMetadataWithNoAttachments() {
    var message = new Message();
    message.setId("msg-1");
    message.setConversationId("conv-1");
    message.setSubject("Invoice");
    message.setSender(graphRecipient("Vendor", "vendor@example.com"));
    message.setToRecipients(List.of(graphRecipient("Me", "me@example.com")));
    message.setReceivedDateTime(OffsetDateTime.parse("2025-01-15T10:30:00Z"));
    var pdf = new EmailAttachmentMetadata("att-1", "invoice.pdf", "application/pdf", 2048L, false);

    var result = GraphApiMapper.toEmailMessage(message, List.of(pdf));

    assertThat(result.id()).isEqualTo("msg-1");
    assertThat(result.conversationId()).isEqualTo("conv-1");
    assertThat(result.subject()).isEqualTo("Invoice");
    assertThat(result.sender().address()).isEqualTo("vendor@example.com");
    assertThat(result.recipients())
        .singleElement()
        .extracting(io.camunda.connector.microsoft.email.model.output.EmailAddress::address)
        .isEqualTo("me@example.com");
    assertThat(result.attachmentMetadata()).containsExactly(pdf);
    assertThat(result.attachments()).isEmpty();
  }

  @Test
  void toEmailMessage_withoutMetadata_yieldsEmptyList() {
    var message = new Message();
    message.setId("msg-2");

    var result = GraphApiMapper.toEmailMessage(message, List.of());

    assertThat(result.attachmentMetadata()).isEmpty();
  }

  @Test
  void withAttachments_setsDocumentsAndKeepsMetadataAndCommonFields() {
    var source =
        new EmailMessage(
            "msg-1",
            "conv-1",
            new io.camunda.connector.microsoft.email.model.output.EmailAddress(
                "Vendor", "vendor@example.com"),
            List.of(),
            List.of(),
            List.of(),
            "Invoice",
            "body",
            "text",
            OffsetDateTime.parse("2025-01-15T10:30:00Z"),
            List.of(
                new EmailAttachmentMetadata(
                    "att-1", "invoice.pdf", "application/pdf", 2048L, false)),
            List.of());
    var downloaded = Mockito.mock(Document.class);

    var result = GraphApiMapper.withAttachments(source, List.of(downloaded));

    assertThat(result.id()).isEqualTo("msg-1");
    assertThat(result.subject()).isEqualTo("Invoice");
    assertThat(result.attachments()).containsExactly(downloaded);
    assertThat(result.attachmentMetadata()).containsExactly(source.attachmentMetadata().get(0));
  }

  @Test
  void getSelect_includesHasAttachments() {
    assertThat(EmailMessage.getSelect()).contains("hasAttachments");
  }
}
