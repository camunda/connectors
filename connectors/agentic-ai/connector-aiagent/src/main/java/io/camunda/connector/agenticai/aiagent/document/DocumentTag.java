/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import java.util.regex.Pattern;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.Nullable;

/**
 * Reads the {@code <doc id="..." />} tag of a document out of text that the model wrote, for
 * example a tool argument. The tag the model sees is rendered by {@link
 * io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent#toTag()}.
 */
public final class DocumentTag {

  /** A text that is exactly one tag: {@code <doc}, optional attributes, {@code />}. */
  private static final Pattern SINGLE_TAG = Pattern.compile("<doc(\\s[^<>]*)?/>");

  private static final Pattern ID_ATTRIBUTE = Pattern.compile("(?:^|\\s)id=\"([^\"]*)\"");

  private DocumentTag() {}

  /**
   * Returns the id of the document if the text is exactly one tag, surrounded by whitespace at
   * most, and the tag has a non-blank {@code id} attribute; the value is XML-unescaped. Returns
   * {@code null} for any other text: text around the tag, more than one tag, or a tag without an
   * id.
   */
  public static @Nullable DocumentId idOfSingleTag(String text) {
    final var tag = SINGLE_TAG.matcher(text.strip());
    if (!tag.matches() || tag.group(1) == null) {
      return null;
    }
    final var id = ID_ATTRIBUTE.matcher(tag.group(1));
    if (!id.find()) {
      return null;
    }
    final var unescaped = StringEscapeUtils.unescapeXml(id.group(1));
    return unescaped.isBlank() ? null : DocumentId.of(unescaped);
  }
}
