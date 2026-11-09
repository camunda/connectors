/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The document factory of one agent job: it resolves each document reference once and returns the
 * same document for it until the job ends.
 *
 * <p>The content guard, the expansion before a model call and the history thus read the same
 * document. An external document downloads its URL once and keeps the response, so the model gets
 * the content that the guard checked, and the URL is downloaded once per job.
 *
 * <p>The documents are held by this factory only. It belongs to the execution context of the job
 * and is never shared between jobs, so the documents are released with the context when the job
 * ends.
 */
public final class JobDocumentFactory implements DocumentFactory {

  private final DocumentFactory delegate;
  private final Map<DocumentReference, Document> resolved = new ConcurrentHashMap<>();

  public JobDocumentFactory(DocumentFactory delegate) {
    this.delegate = delegate;
  }

  @Override
  public Document resolve(DocumentReference reference) {
    return resolved.computeIfAbsent(reference, delegate::resolve);
  }

  @Override
  public Document create(DocumentCreationRequest request) {
    return delegate.create(request);
  }
}
