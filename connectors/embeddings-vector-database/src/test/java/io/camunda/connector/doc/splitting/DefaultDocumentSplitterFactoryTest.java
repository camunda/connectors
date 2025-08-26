/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.doc.splitting;

import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import io.camunda.connector.fixture.DocumentSplitterFixture;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DefaultDocumentSplitterFactoryTest {

  @Test
  void noopDocumentSplitter() {
    Document stub = Mockito.spy(new DefaultDocument("I am stub"));
    final var factory = new DefaultDocumentSplitterFactory();

    final var splitter =
        factory.createDocumentSplitter(DocumentSplitterFixture.noopDocumentSplitter());
    splitter.split(stub);

    Mockito.verify(stub, Mockito.times(1)).toTextSegment();
  }

  @Test
  void recursiveDocumentSplitter() {
    final var factory = new DefaultDocumentSplitterFactory();

    final var splitter =
        factory.createDocumentSplitter(DocumentSplitterFixture.documentSplitterRecursive());

    Assertions.assertThat(splitter).isInstanceOf(DocumentByParagraphSplitter.class);
  }
}
