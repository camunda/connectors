/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.kiota.serialization.JsonParseNodeFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * MicrosoftMailClient relies on {@code instanceof FileAttachment} to decide which attachments to
 * keep, which in turn relies on the Kiota client resolving the correct subtype from {@code
 * @odata.type} even though the query restricts {@code $select} to metadata-only fields. This
 * pins that assumption against the real Kiota discriminator logic, using a payload shaped exactly
 * like the one {@code fetchAttachmentMetadata} requests.
 */
class GraphAttachmentDeserializationTest {

  @Test
  void resolvesFileAttachmentFromMetadataOnlySelectPayload() {
    String json =
        """
        {
          "@odata.type": "#microsoft.graph.fileAttachment",
          "id": "att-1",
          "name": "invoice.pdf",
          "contentType": "application/pdf",
          "size": 2048,
          "isInline": false
        }
        """;

    var parseNode =
        new JsonParseNodeFactory()
            .getParseNode(
                "application/json",
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

    Attachment attachment = parseNode.getObjectValue(Attachment::createFromDiscriminatorValue);

    assertThat(attachment).isInstanceOf(FileAttachment.class);
  }

  @Test
  void fallsBackToBaseAttachmentWhenDiscriminatorIsMissing() {
    // Documents the failure mode this whole test class exists to rule out: if Graph ever
    // stopped including @odata.type under a restricted $select, every attachment would
    // silently resolve to the base type instead of FileAttachment.
    String json =
        """
        {
          "id": "att-1",
          "name": "invoice.pdf",
          "contentType": "application/pdf",
          "size": 2048,
          "isInline": false
        }
        """;

    var parseNode =
        new JsonParseNodeFactory()
            .getParseNode(
                "application/json",
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

    Attachment attachment = parseNode.getObjectValue(Attachment::createFromDiscriminatorValue);

    assertThat(attachment).isNotInstanceOf(FileAttachment.class);
  }
}
