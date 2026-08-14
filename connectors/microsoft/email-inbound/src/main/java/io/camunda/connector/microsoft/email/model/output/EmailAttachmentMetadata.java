/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.output;

/**
 * Lightweight metadata for an email attachment.
 *
 * <p>Unlike {@link io.camunda.connector.api.document.Document}, this record deliberately does not
 * carry the attachment content ({@code contentBytes}). It is populated at poll time, before the
 * activation condition is evaluated, so that conditions can filter on attachment properties (e.g.
 * file type) without triggering an expensive content download.
 */
public record EmailAttachmentMetadata(
    String id, String name, String contentType, Long size, Boolean isInline) {}
