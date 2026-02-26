/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ChatMessageAttachment;
import io.camunda.connector.api.error.ConnectorInputException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CardAttachmentHelperTest {

  @Test
  void shouldNotSetAttachments_whenAttachmentsJsonIsNull() {
    ChatMessage chatMessage = new ChatMessage();
    CardAttachmentHelper.configureMessageBody(chatMessage, "Hello", "TEXT", null);

    assertThat(chatMessage.getAttachments()).isNull();
    assertThat(chatMessage.getBody().getContent()).isEqualTo("Hello");
    assertThat(chatMessage.getBody().getContentType()).isEqualTo(BodyType.Text);
  }

  @Test
  void shouldNotSetAttachments_whenAttachmentsJsonIsEmpty() {
    ChatMessage chatMessage = new ChatMessage();
    CardAttachmentHelper.configureMessageBody(chatMessage, "Hello", "HTML", "");

    assertThat(chatMessage.getAttachments()).isNull();
    assertThat(chatMessage.getBody().getContent()).isEqualTo("Hello");
    assertThat(chatMessage.getBody().getContentType()).isEqualTo(BodyType.Html);
  }

  @Test
  void shouldNotSetAttachments_whenAttachmentsJsonIsBlank() {
    ChatMessage chatMessage = new ChatMessage();
    CardAttachmentHelper.configureMessageBody(chatMessage, "Hello", "TEXT", "   ");

    assertThat(chatMessage.getAttachments()).isNull();
    assertThat(chatMessage.getBody().getContent()).isEqualTo("Hello");
  }

  @Test
  void shouldParseArrayAndAutoAppendTags() {
    String json =
        "[{\"id\":\"att1\",\"contentType\":\"application/vnd.microsoft.card.thumbnail\","
            + "\"content\":\"{\\\"title\\\":\\\"Test\\\"}\"}]";

    ChatMessage chatMessage = new ChatMessage();
    CardAttachmentHelper.configureMessageBody(chatMessage, "Hello", "TEXT", json);

    List<ChatMessageAttachment> attachments = chatMessage.getAttachments();
    assertThat(attachments).hasSize(1);
    assertThat(attachments.get(0).getId()).isEqualTo("att1");
    assertThat(attachments.get(0).getContentType())
        .isEqualTo("application/vnd.microsoft.card.thumbnail");
    assertThat(attachments.get(0).getContent()).isEqualTo("{\"title\":\"Test\"}");

    // Auto-append should switch to HTML and add attachment tag
    assertThat(chatMessage.getBody().getContentType()).isEqualTo(BodyType.Html);
    assertThat(chatMessage.getBody().getContent())
        .isEqualTo("Hello<attachment id=\"att1\"></attachment>");
  }

  @Test
  void shouldParseSingleObjectAsArray() {
    String json =
        "{\"id\":\"single1\",\"contentType\":\"application/vnd.microsoft.card.adaptive\","
            + "\"content\":\"{}\"}";

    ChatMessage chatMessage = new ChatMessage();
    CardAttachmentHelper.configureMessageBody(chatMessage, "Body", "HTML", json);

    List<ChatMessageAttachment> attachments = chatMessage.getAttachments();
    assertThat(attachments).hasSize(1);
    assertThat(attachments.get(0).getId()).isEqualTo("single1");
    assertThat(chatMessage.getBody().getContent())
        .isEqualTo("Body<attachment id=\"single1\"></attachment>");
  }

  @Test
  void shouldNotAppendTag_whenAlreadyPresent() {
    String json =
        "[{\"id\":\"att1\",\"contentType\":\"application/vnd.microsoft.card.thumbnail\"}]";
    String content = "Hello<attachment id=\"att1\"></attachment>";

    ChatMessage chatMessage = new ChatMessage();
    CardAttachmentHelper.configureMessageBody(chatMessage, content, "HTML", json);

    assertThat(chatMessage.getBody().getContent()).isEqualTo(content);
    assertThat(chatMessage.getBody().getContentType()).isEqualTo(BodyType.Html);
    assertThat(chatMessage.getAttachments()).hasSize(1);
  }

  @Test
  void shouldHandleMultipleAttachments() {
    String json =
        "[{\"id\":\"a1\",\"contentType\":\"application/vnd.microsoft.card.thumbnail\"},"
            + "{\"id\":\"a2\",\"contentType\":\"application/vnd.microsoft.card.adaptive\"}]";

    ChatMessage chatMessage = new ChatMessage();
    CardAttachmentHelper.configureMessageBody(chatMessage, "Cards", "TEXT", json);

    assertThat(chatMessage.getAttachments()).hasSize(2);
    assertThat(chatMessage.getBody().getContent())
        .isEqualTo("Cards<attachment id=\"a1\"></attachment><attachment id=\"a2\"></attachment>");
    assertThat(chatMessage.getBody().getContentType()).isEqualTo(BodyType.Html);
  }

  @Test
  void shouldPreserveOptionalFields() {
    String json =
        "[{\"id\":\"att1\",\"contentType\":\"application/vnd.microsoft.card.thumbnail\","
            + "\"contentUrl\":\"https://example.com\",\"name\":\"mycard\","
            + "\"thumbnailUrl\":\"https://example.com/thumb.png\"}]";

    ChatMessage chatMessage = new ChatMessage();
    CardAttachmentHelper.configureMessageBody(chatMessage, "Test", "HTML", json);

    ChatMessageAttachment att = chatMessage.getAttachments().get(0);
    assertThat(att.getContentUrl()).isEqualTo("https://example.com");
    assertThat(att.getName()).isEqualTo("mycard");
    assertThat(att.getThumbnailUrl()).isEqualTo("https://example.com/thumb.png");
  }

  @Test
  void shouldPreserveAdditionalData() {
    String json = "[{\"id\":\"att1\",\"contentType\":\"test\",\"customField\":\"customValue\"}]";

    ChatMessage chatMessage = new ChatMessage();
    CardAttachmentHelper.configureMessageBody(chatMessage, "Test", "HTML", json);

    ChatMessageAttachment att = chatMessage.getAttachments().get(0);
    assertThat(att.getAdditionalData()).containsEntry("customField", "customValue");
  }

  @Test
  void shouldIgnoreNullFields() {
    String json = "[{\"id\":\"att1\",\"contentType\":\"test\",\"contentUrl\":null,\"name\":null}]";

    ChatMessage chatMessage = new ChatMessage();
    CardAttachmentHelper.configureMessageBody(chatMessage, "Test", "HTML", json);

    ChatMessageAttachment att = chatMessage.getAttachments().get(0);
    assertThat(att.getContentUrl()).isNull();
    assertThat(att.getName()).isNull();
  }

  @Test
  void shouldThrowOnInvalidJson() {
    ChatMessage chatMessage = new ChatMessage();
    assertThatThrownBy(
            () ->
                CardAttachmentHelper.configureMessageBody(chatMessage, "Test", "HTML", "not json"))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("Invalid JSON in attachmentsJson");
  }

  @Test
  void shouldThrowOnNonObjectNonArrayJson() {
    ChatMessage chatMessage = new ChatMessage();
    assertThatThrownBy(
            () ->
                CardAttachmentHelper.configureMessageBody(
                    chatMessage, "Test", "HTML", "\"just a string\""))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("attachmentsJson must be a JSON object or array");
  }

  @Test
  void shouldUseDefaultBodyType_whenBodyTypeIsNull() {
    ChatMessage chatMessage = new ChatMessage();
    CardAttachmentHelper.configureMessageBody(chatMessage, "Hello", null, null);

    assertThat(chatMessage.getBody().getContentType()).isEqualTo(BodyType.Text);
  }
}
