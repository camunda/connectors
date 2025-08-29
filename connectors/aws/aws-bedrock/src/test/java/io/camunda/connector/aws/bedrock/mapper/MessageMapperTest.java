/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.mapper;

import static io.camunda.connector.aws.bedrock.BaseTest.readData;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.aws.bedrock.model.BedrockContent;
import io.camunda.connector.aws.bedrock.model.BedrockMessage;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.connector.test.TestDocument;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

class MessageMapperTest {

  private final BedrockContentMapper bedrockContentMapper =
      new BedrockContentMapper(new DocumentMapper());
  private final MessageMapper messageMapper = new MessageMapper(bedrockContentMapper);

  @Test
  void mapToMessages() {
    String msg = "Hello World!";
    String role = "user";
    var bedrockContent = bedrockContentMapper.messageToBedrockContent(msg);

    List<Message> messagesResult =
        messageMapper.mapToMessages(List.of(new BedrockMessage(role, List.of(bedrockContent))));

    List<Message> messagesExpected =
        List.of(
            Message.builder()
                .content(bedrockContentMapper.mapToContentBlocks(List.of(bedrockContent)))
                .role(role)
                .build());

    assertThat(messagesResult).isEqualTo(messagesExpected);
  }

  @Test
  void mapToMessage() {
    String msg = "Hello World!";
    String role = "user";
    var bedrockContent = bedrockContentMapper.messageToBedrockContent(msg);

    var message = messageMapper.mapToMessage(new BedrockMessage(role, List.of(bedrockContent)));

    assertThat(message.role().toString()).isEqualTo(role);
    assertThat(message.content()).isEqualTo(List.of(ContentBlock.fromText(msg)));
  }

  @Test
  void mapToBedrockMessage() {
    String msg = "Hello World!";
    String role = "user";

    Message message = Message.builder().content(ContentBlock.fromText(msg)).role(role).build();

    var bedrockMessageResult = messageMapper.mapToBedrockMessage(message);

    var bedrockMessageExpected =
        new BedrockMessage(role, List.of(bedrockContentMapper.messageToBedrockContent(msg)));

    assertThat(bedrockMessageResult).isEqualTo(bedrockMessageExpected);
  }

  @Test
  void mapToBedrockMessageWithDocumentsAndMessage() throws IOException {
    String path = "src/test/resources/converse/image-document.json";

    var document = prepareDocument(path);
    String msg = "Hello World!";

    var bedrockMessageResult = messageMapper.mapToBedrockMessage(List.of(document), msg);

    var documentContent = new BedrockContent(document);
    var textContent = new BedrockContent(msg);

    var bedrockMessageExpected = new BedrockMessage("user", List.of(documentContent, textContent));

    assertThat(bedrockMessageResult).isEqualTo(bedrockMessageExpected);
  }

  private Document prepareDocument(String path) throws IOException {
    var documentReference = mock(DocumentReference.CamundaDocumentReference.class);

    var docMetadata = readData(path, DocumentReferenceModel.CamundaDocumentMetadataModel.class);
    return new TestDocument(new byte[0], docMetadata, documentReference, "id");
  }
}
