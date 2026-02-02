/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message.content;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

/**
 * Represents an embedded resource content from MCP tool calls.
 *
 * <p>Embedded resources can contain either text or binary data, identified by a URI.
 */
public record EmbeddedResourceContent(
    EmbeddedResource resource,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements Content {

  public EmbeddedResourceContent {
    if (resource == null) {
      throw new IllegalArgumentException("Embedded resource cannot be null");
    }
  }

  public static EmbeddedResourceContent embeddedResource(EmbeddedResource resource) {
    return new EmbeddedResourceContent(resource, null);
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
  @JsonSubTypes({
    @JsonSubTypes.Type(value = TextResource.class),
    @JsonSubTypes.Type(value = BlobResource.class),
    @JsonSubTypes.Type(value = EmbeddedResourceBlobDocumentContent.class)
  })
  public sealed interface EmbeddedResource
      permits TextResource, BlobResource, EmbeddedResourceBlobDocumentContent {}

  /** Text-based embedded resource. */
  public record TextResource(String uri, String mimeType, String text) implements EmbeddedResource {
    public TextResource {
      if (uri == null) {
        throw new IllegalArgumentException("URI cannot be null");
      }
    }
  }

  /** Binary embedded resource. */
  public record BlobResource(String uri, String mimeType, byte[] blob) implements EmbeddedResource {
    public BlobResource {
      if (uri == null) {
        throw new IllegalArgumentException("URI cannot be null");
      }
      if (blob == null) {
        throw new IllegalArgumentException("Blob cannot be null");
      }
    }
  }
}
