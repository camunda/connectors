/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.utils;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Langchain4j {@link InputGuardrail} that detects and sanitizes prompt-injection patterns in user
 * messages before they are sent to the LLM.
 *
 * <p>This guardrail implements the Langchain4j {@code InputGuardrail} interface so it can be used
 * both as a standalone validator (by calling {@link #validate(UserMessage)} directly) and, if the
 * IDP connector ever migrates to Langchain4j's {@code AiServices} proxy, with the
 * {@code @InputGuardrails} annotation.
 *
 * <p>When a prompt-injection pattern is detected, the guardrail does <b>not</b> reject the message
 * outright; instead it rewrites the text content with the offending patterns redacted, so
 * extraction can still proceed on the legitimate parts of the document.
 *
 * @see GuardrailsUtil#containsPromptInjectionPattern(String)
 * @see GuardrailsUtil#sanitizeLlmInput(String)
 */
public class PromptInjectionInputGuardrail implements InputGuardrail {

  private static final Logger LOGGER = LoggerFactory.getLogger(PromptInjectionInputGuardrail.class);

  /** Singleton instance — the guardrail is stateless. */
  private static final PromptInjectionInputGuardrail INSTANCE = new PromptInjectionInputGuardrail();

  public static PromptInjectionInputGuardrail getInstance() {
    return INSTANCE;
  }

  /**
   * Validates and potentially rewrites the {@link UserMessage}.
   *
   * <p>Scans every {@link TextContent} block in the message for prompt-injection patterns. If any
   * are found the text is sanitized (patterns are replaced with {@code [REDACTED]}) and the
   * guardrail returns a {@link InputGuardrailResult#successWith(String)} containing the cleaned
   * text so that the framework (or our manual call-site) can substitute it.
   */
  @Override
  public InputGuardrailResult validate(UserMessage userMessage) {
    if (userMessage == null) {
      return success();
    }

    StringBuilder combinedText = new StringBuilder();
    boolean hasTextContent = false;

    for (Content content : userMessage.contents()) {
      if (content.type() == ContentType.TEXT) {
        hasTextContent = true;
        combinedText.append(((TextContent) content).text());
      }
    }

    if (!hasTextContent) {
      return success();
    }

    String originalText = combinedText.toString();
    if (GuardrailsUtil.containsPromptInjectionPattern(originalText)) {
      String sanitized = GuardrailsUtil.sanitizeLlmInput(originalText);
      LOGGER.warn(
          "Prompt-injection pattern detected in user message input — text has been sanitized");
      return successWith(sanitized);
    }

    return success();
  }
}
