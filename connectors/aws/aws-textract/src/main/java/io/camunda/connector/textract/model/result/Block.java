/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model.result;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * Connector-owned mirror of the AWS SDK v2 {@code Block} shape, used by both the sync-analyze
 * ({@link AnalyzeDocumentResult}) and the polling/merge ({@link GetDocumentAnalysisResult})
 * results. The v2 {@code Block} class exposes fluent accessors only (no JavaBean getters); mapping
 * it into this record restores the documented v1 JSON field order and the explicit-null-for-unset
 * behavior that the runtime's {@code ObjectMapper} would otherwise silently drop (see #7967 / #7977
 * for the same hazard in aws-eventbridge).
 *
 * <p>{@code geometry.rotationAngle} exists on the v2 SDK model but was never part of the v1 JSON
 * shape this connector documented, so it is intentionally NOT mapped here.
 */
@JsonPropertyOrder({
  "blockType",
  "confidence",
  "text",
  "textType",
  "rowIndex",
  "columnIndex",
  "rowSpan",
  "columnSpan",
  "geometry",
  "id",
  "relationships",
  "entityTypes",
  "selectionStatus",
  "page",
  "query"
})
public record Block(
    String blockType,
    Float confidence,
    String text,
    String textType,
    Integer rowIndex,
    Integer columnIndex,
    Integer rowSpan,
    Integer columnSpan,
    Geometry geometry,
    String id,
    List<Relationship> relationships,
    List<String> entityTypes,
    String selectionStatus,
    Integer page,
    Query query) {

  public static Block from(final software.amazon.awssdk.services.textract.model.Block block) {
    if (block == null) {
      return null;
    }
    return new Block(
        block.blockTypeAsString(),
        block.confidence(),
        block.text(),
        block.textTypeAsString(),
        block.rowIndex(),
        block.columnIndex(),
        block.rowSpan(),
        block.columnSpan(),
        Geometry.from(block.geometry()),
        block.id(),
        block.hasRelationships() ? mapRelationships(block.relationships()) : null,
        block.hasEntityTypes() ? block.entityTypesAsStrings() : null,
        block.selectionStatusAsString(),
        block.page(),
        Query.from(block.query()));
  }

  private static List<Relationship> mapRelationships(
      final List<software.amazon.awssdk.services.textract.model.Relationship> relationships) {
    return relationships.stream().map(Relationship::from).toList();
  }

  @JsonPropertyOrder({"boundingBox", "polygon"})
  public record Geometry(BoundingBox boundingBox, List<Point> polygon) {

    static Geometry from(final software.amazon.awssdk.services.textract.model.Geometry geometry) {
      if (geometry == null) {
        return null;
      }
      return new Geometry(
          BoundingBox.from(geometry.boundingBox()),
          geometry.hasPolygon() ? mapPolygon(geometry.polygon()) : null);
    }

    private static List<Point> mapPolygon(
        final List<software.amazon.awssdk.services.textract.model.Point> polygon) {
      return polygon.stream().map(Point::from).toList();
    }
  }

  @JsonPropertyOrder({"width", "height", "left", "top"})
  public record BoundingBox(Float width, Float height, Float left, Float top) {

    static BoundingBox from(final software.amazon.awssdk.services.textract.model.BoundingBox box) {
      if (box == null) {
        return null;
      }
      return new BoundingBox(box.width(), box.height(), box.left(), box.top());
    }
  }

  @JsonPropertyOrder({"x", "y"})
  public record Point(Float x, Float y) {

    static Point from(final software.amazon.awssdk.services.textract.model.Point point) {
      if (point == null) {
        return null;
      }
      return new Point(point.x(), point.y());
    }
  }

  @JsonPropertyOrder({"type", "ids"})
  public record Relationship(String type, List<String> ids) {

    static Relationship from(
        final software.amazon.awssdk.services.textract.model.Relationship relationship) {
      return new Relationship(
          relationship.typeAsString(), relationship.hasIds() ? relationship.ids() : null);
    }
  }

  @JsonPropertyOrder({"text", "alias", "pages"})
  public record Query(String text, String alias, List<String> pages) {

    static Query from(final software.amazon.awssdk.services.textract.model.Query query) {
      if (query == null) {
        return null;
      }
      return new Query(query.text(), query.alias(), query.hasPages() ? query.pages() : null);
    }
  }
}
