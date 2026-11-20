/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Named.named;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.request.DocumentsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** The validation of the user prompt delivery against the document references. */
@ExtendWith(SpringExtension.class)
@Import(ValidationAutoConfiguration.class)
class DocumentsConfigurationTest {

  @Autowired private Validator validator;

  /** The request data of each flavor, built from the document configuration. */
  static Stream<Arguments> requestData() {
    return Stream.of(
        Arguments.of(
            named(
                "task",
                (Function<DocumentsConfiguration, Object>)
                    documents ->
                        new AgentTaskRequestData(
                            null,
                            new SystemPromptConfiguration("You are a helpful assistant."),
                            new UserPromptConfiguration("Hello", null),
                            null,
                            documents,
                            null,
                            null,
                            null))),
        Arguments.of(
            named(
                "sub-process",
                (Function<DocumentsConfiguration, Object>)
                    documents ->
                        new AgentSubProcessRequestData(
                            new SystemPromptConfiguration("You are a helpful assistant."),
                            new UserPromptConfiguration("Hello", null),
                            documents,
                            null,
                            null,
                            null,
                            null))));
  }

  @Test
  void deliversTheContentOfTheUserPromptDocumentsWithoutADelivery() {
    assertThat(new DocumentsConfiguration(true, null).userPromptDelivery())
        .isEqualTo(DocumentDelivery.CONTENT);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("requestData")
  void rejectsTheDeliveryReferenceWithDocumentReferencesOff(
      Function<DocumentsConfiguration, Object> requestData) {
    assertThat(
            validator.validate(
                requestData.apply(new DocumentsConfiguration(false, DocumentDelivery.REFERENCE))))
        .extracting(
            violation -> violation.getPropertyPath().toString(), ConstraintViolation::getMessage)
        .containsExactly(
            tuple(
                "documents.userPromptDelivery",
                "The user prompt delivery \"Reference\" requires the option \"Use document references\". Select this option, or select the user prompt delivery \"Content\"."));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("requestData")
  void acceptsTheDeliveryContentOrNoDeliveryWithDocumentReferencesOff(
      Function<DocumentsConfiguration, Object> requestData) {
    assertThat(
            validator.validate(
                requestData.apply(new DocumentsConfiguration(false, DocumentDelivery.CONTENT))))
        .isEmpty();
    assertThat(validator.validate(requestData.apply(new DocumentsConfiguration(false, null))))
        .isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("requestData")
  void acceptsEveryDeliveryWithDocumentReferencesOn(
      Function<DocumentsConfiguration, Object> requestData) {
    for (final var delivery :
        new DocumentDelivery[] {DocumentDelivery.REFERENCE, DocumentDelivery.CONTENT, null}) {
      assertThat(validator.validate(requestData.apply(new DocumentsConfiguration(true, delivery))))
          .isEmpty();
    }
  }
}
