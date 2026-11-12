/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentMimeTypesTest {

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {"   "})
  void parseReturnsNullForBlank(String contentType) {
    assertThat(DocumentMimeTypes.parse(contentType)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {";;;", "text/plain; charset=bogus-charset-xyz"})
  void parseReturnsNullForUnparseable(String contentType) {
    assertThat(DocumentMimeTypes.parse(contentType)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"image/png", "Image/Png", "IMAGE/PNG", "  image/png  "})
  void parseNormalizesCaseAndWhitespace(String contentType) {
    assertThat(DocumentMimeTypes.parse(contentType))
        .isNotNull()
        .extracting(ContentType::getMimeType)
        .isEqualTo("image/png");
  }

  @ParameterizedTest
  @ValueSource(strings = {"image/jpeg", "image/png", "image/gif", "image/webp"})
  void isImageTrueForSupportedImageTypes(String contentType) {
    assertThat(DocumentMimeTypes.isImage(DocumentMimeTypes.parse(contentType))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"IMAGE/PNG", "image/png; charset=UTF-8", "Image/Jpeg"})
  void isImageIsCaseAndParameterInsensitive(String contentType) {
    assertThat(DocumentMimeTypes.isImage(DocumentMimeTypes.parse(contentType))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"image/bmp", "image/tiff", "application/pdf", "text/plain"})
  void isImageFalseForOtherTypes(String contentType) {
    assertThat(DocumentMimeTypes.isImage(DocumentMimeTypes.parse(contentType))).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    // text/* wildcard
    "text/plain",
    "text/csv",
    "text/my-custom-format",

    // exact structured-data types
    "application/json",
    "application/xml",
    "application/yaml",
    "application/x-yaml",

    // RFC 6839 structured syntax suffixes
    "application/problem+json",
    "application/atom+xml",

    // +yaml is not RFC 6839-registered but used the same way in the wild (e.g. OpenAPI 3.1)
    "application/vnd.oai.openapi+yaml",

    // case/parameter insensitivity
    "TEXT/PLAIN",
    "APPLICATION/JSON",
    "application/json; charset=UTF-8"
  })
  void isTextIshTrueForTextTypes(String contentType) {
    assertThat(DocumentMimeTypes.isTextIsh(DocumentMimeTypes.parse(contentType))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "application/pdf",
        "image/png",
        "application/octet-stream",
        "application/zip",
        "application/msword",
        // parses fine (no slash validation), but matches none of the text buckets
        "foo_bar"
      })
  void isTextIshFalseForNonTextTypes(String contentType) {
    assertThat(DocumentMimeTypes.isTextIsh(DocumentMimeTypes.parse(contentType))).isFalse();
  }

  @Test
  void isTextIshWorksWithoutGoingThroughParse() {
    // raw ContentType.parse (unlike DocumentMimeTypes.parse) preserves case; isTextIsh
    // lower-cases internally, so it doesn't depend on the caller having normalized first
    assertThat(DocumentMimeTypes.isTextIsh(ContentType.parse("TEXT/PLAIN"))).isTrue();
  }

  @Test
  void isImageWorksWithoutGoingThroughParse() {
    // isSameMimeType is case-insensitive, so isImage doesn't depend on normalized input either
    assertThat(DocumentMimeTypes.isImage(ContentType.parse("IMAGE/PNG"))).isTrue();
  }
}
