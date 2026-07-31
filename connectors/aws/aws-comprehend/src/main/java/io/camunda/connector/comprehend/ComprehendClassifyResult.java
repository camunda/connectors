/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import software.amazon.awssdk.services.comprehend.model.ClassifyDocumentResponse;
import software.amazon.awssdk.services.comprehend.model.DocumentClass;
import software.amazon.awssdk.services.comprehend.model.DocumentLabel;
import software.amazon.awssdk.services.comprehend.model.DocumentTypeListItem;
import software.amazon.awssdk.services.comprehend.model.ErrorsListItem;
import software.amazon.awssdk.services.comprehend.model.ExtractedCharactersListItem;
import software.amazon.awssdk.services.comprehend.model.WarningsListItem;

/**
 * Connector-owned result of a Comprehend {@code ClassifyDocument} call.
 *
 * <p>The AWS SDK v2 model classes ({@link ClassifyDocumentResponse} and friends) expose fluent
 * accessors ({@code classes()}, {@code documentMetadata()}, ...) rather than JavaBean getters.
 * Serializing them directly with the connectors' {@code ObjectMapper} (which disables {@code
 * FAIL_ON_EMPTY_BEANS}) would silently produce {@code {}}. This record maps the v2 response back
 * into the exact JSON shape that the pre-v2 (AWS SDK v1) connector documented and returned,
 * restoring that output contract -- including distinguishing an absent (never-set) list, which v1
 * exposed as {@code null}, from an explicitly empty one (v2's {@code hasXxx()} flag).
 */
@JsonPropertyOrder({
  "sdkResponseMetadata",
  "sdkHttpMetadata",
  "classes",
  "labels",
  "documentMetadata",
  "documentType",
  "errors",
  "warnings"
})
public record ComprehendClassifyResult(
    SdkResponseMetadata sdkResponseMetadata,
    SdkHttpMetadata sdkHttpMetadata,
    List<Classification> classes,
    List<Classification> labels,
    ClassifyDocumentMetadata documentMetadata,
    List<DocumentTypeItem> documentType,
    List<ErrorItem> errors,
    List<WarningItem> warnings) {

  /** Maps an AWS SDK v2 {@link ClassifyDocumentResponse} into the v1-shaped connector result. */
  public static ComprehendClassifyResult from(final ClassifyDocumentResponse response) {
    return new ComprehendClassifyResult(
        SdkResponseMetadata.from(response.responseMetadata()),
        SdkHttpMetadata.from(response.sdkHttpResponse()),
        response.hasClasses() ? mapClasses(response.classes()) : null,
        response.hasLabels() ? mapLabels(response.labels()) : null,
        ClassifyDocumentMetadata.from(response.documentMetadata()),
        response.hasDocumentType() ? mapDocumentType(response.documentType()) : null,
        response.hasErrors() ? mapErrors(response.errors()) : null,
        response.hasWarnings() ? mapWarnings(response.warnings()) : null);
  }

  private static List<Classification> mapClasses(final List<DocumentClass> classes) {
    return classes.stream().map(c -> new Classification(c.name(), c.score(), c.page())).toList();
  }

  private static List<Classification> mapLabels(final List<DocumentLabel> labels) {
    return labels.stream().map(l -> new Classification(l.name(), l.score(), l.page())).toList();
  }

  private static List<DocumentTypeItem> mapDocumentType(
      final List<DocumentTypeListItem> documentType) {
    return documentType.stream()
        .map(item -> new DocumentTypeItem(item.page(), item.typeAsString()))
        .toList();
  }

  private static List<ErrorItem> mapErrors(final List<ErrorsListItem> errors) {
    return errors.stream()
        .map(e -> new ErrorItem(e.page(), e.errorCodeAsString(), e.errorMessage()))
        .toList();
  }

  private static List<WarningItem> mapWarnings(final List<WarningsListItem> warnings) {
    return warnings.stream()
        .map(w -> new WarningItem(w.page(), w.warnCodeAsString(), w.warnMessage()))
        .toList();
  }

  /** Shape shared by both the {@code classes} and {@code labels} v1 JSON arrays. */
  @JsonPropertyOrder({"name", "score", "page"})
  public record Classification(String name, Float score, Integer page) {}

  @JsonPropertyOrder({"pages", "extractedCharacters"})
  public record ClassifyDocumentMetadata(
      Integer pages, List<ExtractedCharacters> extractedCharacters) {

    static ClassifyDocumentMetadata from(
        final software.amazon.awssdk.services.comprehend.model.DocumentMetadata documentMetadata) {
      if (documentMetadata == null) {
        return null;
      }
      List<ExtractedCharacters> extractedCharacters =
          documentMetadata.hasExtractedCharacters()
              ? mapExtractedCharacters(documentMetadata.extractedCharacters())
              : null;
      return new ClassifyDocumentMetadata(documentMetadata.pages(), extractedCharacters);
    }

    private static List<ExtractedCharacters> mapExtractedCharacters(
        final List<ExtractedCharactersListItem> extractedCharacters) {
      return extractedCharacters.stream()
          .map(item -> new ExtractedCharacters(item.page(), item.count()))
          .toList();
    }
  }

  @JsonPropertyOrder({"page", "count"})
  public record ExtractedCharacters(Integer page, Integer count) {}

  @JsonPropertyOrder({"page", "type"})
  public record DocumentTypeItem(Integer page, String type) {}

  @JsonPropertyOrder({"page", "errorCode", "errorMessage"})
  public record ErrorItem(Integer page, String errorCode, String errorMessage) {}

  @JsonPropertyOrder({"page", "warnCode", "warnMessage"})
  public record WarningItem(Integer page, String warnCode, String warnMessage) {}
}
