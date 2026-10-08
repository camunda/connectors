/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentLinkParameters;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import java.io.InputStream;

/**
 * A document that the model providers render as its serialized Camunda document reference inside
 * the content of a tool result, instead of as its {@code <doc/>} tag. The conversation of an agent
 * instance created without the documents feature holds no tags and no registry, so the reference is
 * the only identity its tool results can show.
 *
 * <p>Everything else is delegated, so the document is read and embedded like the one it wraps.
 */
public record ReferenceRenderedDocument(Document delegate) implements Document {

  @Override
  public DocumentMetadata metadata() {
    return delegate.metadata();
  }

  @Override
  public String asBase64() {
    return delegate.asBase64();
  }

  @Override
  public InputStream asInputStream() {
    return delegate.asInputStream();
  }

  @Override
  public byte[] asByteArray() {
    return delegate.asByteArray();
  }

  @Override
  public DocumentReference reference() {
    return delegate.reference();
  }

  @Override
  public String generateLink(DocumentLinkParameters parameters) {
    return delegate.generateLink(parameters);
  }
}
