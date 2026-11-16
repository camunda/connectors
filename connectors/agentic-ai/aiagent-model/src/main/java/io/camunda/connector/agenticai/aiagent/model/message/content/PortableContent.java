/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.content;

/**
 * A content block that any chat model provider can receive: a text, a document or an object. The
 * system, user and tool call result messages of a chat request hold only these blocks.
 */
public sealed interface PortableContent extends ChatContent
    permits TextContent, DocumentContent, ObjectContent {}
