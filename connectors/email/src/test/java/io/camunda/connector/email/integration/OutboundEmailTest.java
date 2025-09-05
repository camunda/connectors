/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.email.client.jakarta.outbound.JakartaEmailActionExecutor;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.response.DeleteEmailResponse;
import io.camunda.connector.email.response.ListEmailsResponse;
import io.camunda.connector.email.response.ReadEmailResponse;
import io.camunda.connector.email.response.SearchEmailsResponse;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import jakarta.mail.Message;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class OutboundEmailTest extends BaseEmailTest {

  DocumentFactory documentFactory = new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
  ObjectMapper objectMapper = new ObjectMapper();

  JakartaEmailActionExecutor jakartaEmailActionExecutor =
      JakartaEmailActionExecutor.create(
          new JakartaUtils(), ConnectorsObjectMapperSupplier.getCopy());
  private OutboundConnectorContextBuilder contextBuilder = OutboundConnectorContextBuilder.create();

  private static Stream<String> getStreamFromPath(String path) {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    try {
      return objectMapper
          .readValue(Files.readString(Path.of(path)), new TypeReference<List<JsonNode>>() {})
          .stream()
          .map(JsonNode::toString);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Stream<String> createEmailOutboundSmtpConnectorProperties() {
    return getStreamFromPath(
        "src/test/resources/integration/outbound-smtp-connector-happy-path.json");
  }

  public static Stream<String> createEmailOutboundPop3DeleteConnectorProperties() {
    return getStreamFromPath(
        "src/test/resources/integration/outbound-pop3-delete-connector-happy-path.json");
  }

  public static Stream<String> createEmailOutboundPop3SearchConnectorProperties() {
    return getStreamFromPath(
        "src/test/resources/integration/outbound-pop3-search-connector-happy-path.json");
  }

  public static Stream<String> createEmailOutboundPop3ListConnectorProperties() {
    return getStreamFromPath(
        "src/test/resources/integration/outbound-pop3-list-connector-happy-path.json");
  }

  public static Stream<String> createEmailOutboundPop3ReadConnectorProperties() {
    return getStreamFromPath(
        "src/test/resources/integration/outbound-pop3-read-connector-happy-path.json");
  }

  public static Stream<String> createEmailOutboundImapDeleteConnectorProperties() {
    return getStreamFromPath(
        "src/test/resources/integration/outbound-imap-delete-connector-happy-path.json");
  }

  public static Stream<String> createEmailOutboundImapSearchConnectorProperties() {
    return getStreamFromPath(
        "src/test/resources/integration/outbound-imap-search-connector-happy-path.json");
  }

  public static Stream<String> createEmailOutboundImapListConnectorProperties() {
    return getStreamFromPath(
        "src/test/resources/integration/outbound-imap-list-connector-happy-path.json");
  }

  public static Stream<String> createEmailOutboundImapReadConnectorProperties() {
    return getStreamFromPath(
        "src/test/resources/integration/outbound-imap-read-connector-happy-path.json");
  }

  @BeforeEach
  public void beforeEach() {
    super.reset();
  }

  @ParameterizedTest
  @MethodSource("createEmailOutboundSmtpConnectorProperties")
  public void shouldSendEmailSmtp(String emailRequest) throws IOException {

    JsonNode jsonNode = objectMapper.readValue(emailRequest, JsonNode.class);

    JsonNode jsonNodeDocumentList = jsonNode.get("data").get("smtpAction").get("attachments");

    Path attachment = Path.of("src/test/resources/img/img.png");
    if (jsonNodeDocumentList != null) {
      JsonNode jsonNodeDocument = jsonNodeDocumentList.get(0);
      this.documentFactory.create(
          DocumentCreationRequest.from(Files.readAllBytes(attachment))
              .documentId(jsonNodeDocument.get("documentId").asText())
              .fileName(jsonNodeDocument.get("metadata").get("fileName").asText())
              .build());
    }

    emailRequest = emailRequest.replace("{{PORT}}", super.getUnsecureSmtpPort());
    OutboundConnectorContext outboundConnectorContext =
        this.contextBuilder.variables(emailRequest).build();

    this.jakartaEmailActionExecutor.execute(outboundConnectorContext);

    Message message = Arrays.stream(super.getLastReceivedEmails()).findFirst().get();

    JsonNode jsonNodeValue = jsonNode.get("data").get("smtpAction");

    Assertions.assertNotNull(message);
    if (jsonNodeValue.get("body") != null) {
      Assertions.assertEquals(jsonNodeValue.get("body").asText(), getBodyAsText(message));
    }
    Assertions.assertEquals(jsonNodeValue.get("to").asText(), getTo(message).getFirst());
    Assertions.assertEquals(jsonNodeValue.get("subject").asText(), getEmailSubject(message));
    if (jsonNodeValue.get("htmlBody") != null) {
      Assertions.assertEquals(jsonNodeValue.get("htmlBody").asText(), getBodyAsHtml(message));
    }
    if (jsonNodeDocumentList != null) {
      Assertions.assertArrayEquals(getBodyAsByteArray(message), Files.readAllBytes(attachment));
    }
  }

  @ParameterizedTest
  @MethodSource("createEmailOutboundPop3DeleteConnectorProperties")
  public void shouldUsePop3Delete(String emailRequest) {
    super.sendEmail("test@test.com", "subject", "Content");
    String messageId = getLastMessageId();

    emailRequest = emailRequest.replace("{{PORT}}", super.getUnsecurePop3Port());
    emailRequest = emailRequest.replace("{{MESSAGE_ID}}", messageId);
    OutboundConnectorContext outboundConnectorContext =
        this.contextBuilder.variables(emailRequest).build();

    Object object = this.jakartaEmailActionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(DeleteEmailResponse.class, object);
    Assertions.assertTrue(((DeleteEmailResponse) object).deleted());
  }

  @ParameterizedTest
  @MethodSource("createEmailOutboundImapDeleteConnectorProperties")
  public void shouldUseImapDelete(String emailRequest) {
    super.sendEmail("test@test.com", "subject", "Content");
    String messageId = getLastMessageId();

    emailRequest = emailRequest.replace("{{PORT}}", super.getUnsecureImapPort());
    emailRequest = emailRequest.replace("{{MESSAGE_ID}}", messageId);
    OutboundConnectorContext outboundConnectorContext =
        this.contextBuilder.variables(emailRequest).build();

    Object object = this.jakartaEmailActionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(DeleteEmailResponse.class, object);
    Assertions.assertTrue(((DeleteEmailResponse) object).deleted());
  }

  @ParameterizedTest
  @MethodSource("createEmailOutboundPop3SearchConnectorProperties")
  public void shouldUsePop3Search(String emailRequest) {
    super.sendEmail("test@test.com", "subject", "Content");

    emailRequest = emailRequest.replace("{{PORT}}", super.getUnsecurePop3Port());
    OutboundConnectorContext outboundConnectorContext =
        this.contextBuilder.variables(emailRequest).build();

    Object object = this.jakartaEmailActionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(List.class, object);
    Object searchResult = ((List<?>) object).getFirst();
    Assertions.assertInstanceOf(SearchEmailsResponse.class, searchResult);
    Assertions.assertEquals("subject", ((SearchEmailsResponse) searchResult).subject());
    Assertions.assertEquals(
        super.getLastMessageId(), ((SearchEmailsResponse) searchResult).messageId());
  }

  @ParameterizedTest
  @MethodSource("createEmailOutboundImapSearchConnectorProperties")
  public void shouldUseImapSearch(String emailRequest) {
    super.sendEmail("test@test.com", "subject", "Content");

    emailRequest = emailRequest.replace("{{PORT}}", super.getUnsecureImapPort());
    OutboundConnectorContext outboundConnectorContext =
        this.contextBuilder.variables(emailRequest).build();

    Object object = this.jakartaEmailActionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(List.class, object);
    Object searchResult = ((List<?>) object).getFirst();
    Assertions.assertInstanceOf(SearchEmailsResponse.class, searchResult);
    Assertions.assertEquals("subject", ((SearchEmailsResponse) searchResult).subject());
    Assertions.assertEquals(
        super.getLastMessageId(), ((SearchEmailsResponse) searchResult).messageId());
  }

  @ParameterizedTest
  @MethodSource("createEmailOutboundPop3ListConnectorProperties")
  public void shouldUsePop3List(String emailRequest) throws JsonProcessingException {
    JsonNode jsonNode = objectMapper.readValue(emailRequest, JsonNode.class);

    super.sendEmail("test@test.com", "subject 1", "Content 1");
    super.sendEmail("test2@test.com", "subject 2", "Content 2");

    emailRequest = emailRequest.replace("{{PORT}}", super.getUnsecurePop3Port());
    OutboundConnectorContext outboundConnectorContext =
        this.contextBuilder.variables(emailRequest).build();

    Object object = this.jakartaEmailActionExecutor.execute(outboundConnectorContext);

    JsonNode jsonNodeValue = jsonNode.get("data").get("pop3Action");

    Assertions.assertInstanceOf(List.class, object);
    Assertions.assertTrue(((List) object).size() < jsonNodeValue.get("maxToBeRead").asInt());

    switch (jsonNodeValue.get("sortField").asText()) {
      case "SENT_DATE" -> {
        if (jsonNodeValue.get("sortOrder").asText().equals("ASC")) {
          Assertions.assertEquals(
              "subject 1", ((ListEmailsResponse) ((List<?>) object).getFirst()).subject());
          Assertions.assertEquals(
              "subject 2", ((ListEmailsResponse) ((List<?>) object).get(1)).subject());
        }
      }
      default ->
          throw new IllegalStateException(
              "Unexpected value: " + jsonNodeValue.get("sortField").asText());
    }
  }

  @ParameterizedTest
  @MethodSource("createEmailOutboundImapListConnectorProperties")
  public void shouldUseImapList(String emailRequest) throws JsonProcessingException {
    JsonNode jsonNode = objectMapper.readValue(emailRequest, JsonNode.class);

    super.sendEmail("test@test.com", "subject 1", "Content 1");
    super.sendEmail("test2@test.com", "subject 2", "Content 2");

    emailRequest = emailRequest.replace("{{PORT}}", super.getUnsecureImapPort());
    OutboundConnectorContext outboundConnectorContext =
        this.contextBuilder.variables(emailRequest).build();

    Object object = this.jakartaEmailActionExecutor.execute(outboundConnectorContext);

    JsonNode jsonNodeValue = jsonNode.get("data").get("imapAction");

    Assertions.assertInstanceOf(List.class, object);
    Assertions.assertTrue(((List) object).size() < jsonNodeValue.get("maxToBeRead").asInt());

    switch (jsonNodeValue.get("sortField").asText()) {
      case "SENT_DATE", "RECEIVED_DATE" -> {
        if (jsonNodeValue.get("sortOrder").asText().equals("ASC")) {
          Assertions.assertEquals(
              "subject 1", ((ListEmailsResponse) ((List<?>) object).getFirst()).subject());
          Assertions.assertEquals(
              "subject 2", ((ListEmailsResponse) ((List<?>) object).get(1)).subject());
        }
      }
      default ->
          throw new IllegalStateException(
              "Unexpected value: " + jsonNodeValue.get("sortField").asText());
    }
  }

  @ParameterizedTest
  @MethodSource("createEmailOutboundPop3ReadConnectorProperties")
  public void shouldUsePop3Read(String emailRequest) {
    super.sendEmail("test@test.com", "subject", "Content");
    String messageId = getLastMessageId();

    emailRequest = emailRequest.replace("{{PORT}}", super.getUnsecurePop3Port());
    emailRequest = emailRequest.replace("{{MESSAGE_ID}}", messageId);
    OutboundConnectorContext outboundConnectorContext =
        this.contextBuilder.variables(emailRequest).build();

    Object object = this.jakartaEmailActionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(ReadEmailResponse.class, object);
    Assertions.assertEquals(((ReadEmailResponse) object).messageId(), messageId);
    Assertions.assertEquals("Content\r\n", ((ReadEmailResponse) object).plainTextBody());
    Assertions.assertEquals("subject", ((ReadEmailResponse) object).subject());
  }

  @ParameterizedTest
  @MethodSource("createEmailOutboundImapReadConnectorProperties")
  public void shouldUseImapRead(String emailRequest) {
    super.sendEmail("test@test.com", "subject", "Content");
    String messageId = getLastMessageId();

    emailRequest = emailRequest.replace("{{PORT}}", super.getUnsecureImapPort());
    emailRequest = emailRequest.replace("{{MESSAGE_ID}}", messageId);
    OutboundConnectorContext outboundConnectorContext =
        this.contextBuilder.variables(emailRequest).build();

    Object object = this.jakartaEmailActionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(ReadEmailResponse.class, object);
    Assertions.assertEquals(((ReadEmailResponse) object).messageId(), messageId);
    Assertions.assertEquals("Content", ((ReadEmailResponse) object).plainTextBody());
    Assertions.assertEquals("subject", ((ReadEmailResponse) object).subject());
  }
}
