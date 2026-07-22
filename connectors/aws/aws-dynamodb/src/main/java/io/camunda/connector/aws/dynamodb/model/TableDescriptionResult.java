/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import software.amazon.awssdk.services.dynamodb.model.ArchivalSummary;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingModeSummary;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexWarmThroughputDescription;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.LocalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.OnDemandThroughput;
import software.amazon.awssdk.services.dynamodb.model.OnDemandThroughputOverride;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputDescription;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputOverride;
import software.amazon.awssdk.services.dynamodb.model.ReplicaDescription;
import software.amazon.awssdk.services.dynamodb.model.ReplicaGlobalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.RestoreSummary;
import software.amazon.awssdk.services.dynamodb.model.SSEDescription;
import software.amazon.awssdk.services.dynamodb.model.StreamSpecification;
import software.amazon.awssdk.services.dynamodb.model.TableClassSummary;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableWarmThroughputDescription;

/**
 * Connector-owned mapping of AWS SDK v2's {@link TableDescription} (returned by both {@code
 * createTable} and {@code describeTable}), reproducing the exact JSON shape v1's raw {@code
 * TableDescription} JavaBean used to produce (see CreateTableOperationTest/
 * DescribeTableOperationTest's golden-JSON tests): the same ~24 top-level fields, in the same
 * shape, including the {@code ssedescription} key spelling and the ISO-8601-with-milliseconds
 * {@code creationDateTime} string (reproduced by converting {@code Instant -> java.util.Date} and
 * letting the production mapper's existing {@code java.util.Date} serializer format it exactly as
 * before -- {@code Instant}'s own jackson-datatype-jsr310 serializer uses a different format).
 *
 * <p>Every nested v2 SDK type (fluent-accessor-only, no JavaBean getters) is explicitly mapped into
 * a connector-owned record, even the ones that are always {@code null} for tables this connector
 * creates (indexes, streams, replicas, restore/archival summaries, SSE) -- so that describing a
 * table that some other application configured with these features never silently serializes to
 * {@code {}}.
 */
@JsonPropertyOrder({
  "attributeDefinitions",
  "tableName",
  "keySchema",
  "tableStatus",
  "creationDateTime",
  "provisionedThroughput",
  "tableSizeBytes",
  "itemCount",
  "tableArn",
  "tableId",
  "billingModeSummary",
  "localSecondaryIndexes",
  "globalSecondaryIndexes",
  "streamSpecification",
  "latestStreamLabel",
  "latestStreamArn",
  "globalTableVersion",
  "replicas",
  "restoreSummary",
  "archivalSummary",
  "tableClassSummary",
  "deletionProtectionEnabled",
  "onDemandThroughput",
  "ssedescription"
})
public record TableDescriptionResult(
    List<AttributeDefinitionResult> attributeDefinitions,
    String tableName,
    List<KeySchemaElementResult> keySchema,
    String tableStatus,
    Date creationDateTime,
    ProvisionedThroughputResult provisionedThroughput,
    Long tableSizeBytes,
    Long itemCount,
    String tableArn,
    String tableId,
    BillingModeSummaryResult billingModeSummary,
    List<LocalSecondaryIndexDescriptionResult> localSecondaryIndexes,
    List<GlobalSecondaryIndexDescriptionResult> globalSecondaryIndexes,
    StreamSpecificationResult streamSpecification,
    String latestStreamLabel,
    String latestStreamArn,
    String globalTableVersion,
    List<ReplicaDescriptionResult> replicas,
    RestoreSummaryResult restoreSummary,
    ArchivalSummaryResult archivalSummary,
    TableClassSummaryResult tableClassSummary,
    Boolean deletionProtectionEnabled,
    OnDemandThroughputResult onDemandThroughput,
    SSEDescriptionResult ssedescription) {

  public static TableDescriptionResult from(final TableDescription table) {
    if (table == null) {
      return null;
    }
    return new TableDescriptionResult(
        mapList(
            table.hasAttributeDefinitions() ? table.attributeDefinitions() : null,
            AttributeDefinitionResult::from),
        table.tableName(),
        mapList(table.hasKeySchema() ? table.keySchema() : null, KeySchemaElementResult::from),
        table.tableStatusAsString(),
        toDate(table.creationDateTime()),
        ProvisionedThroughputResult.from(table.provisionedThroughput()),
        table.tableSizeBytes(),
        table.itemCount(),
        table.tableArn(),
        table.tableId(),
        BillingModeSummaryResult.from(table.billingModeSummary()),
        mapList(
            table.hasLocalSecondaryIndexes() ? table.localSecondaryIndexes() : null,
            LocalSecondaryIndexDescriptionResult::from),
        mapList(
            table.hasGlobalSecondaryIndexes() ? table.globalSecondaryIndexes() : null,
            GlobalSecondaryIndexDescriptionResult::from),
        StreamSpecificationResult.from(table.streamSpecification()),
        table.latestStreamLabel(),
        table.latestStreamArn(),
        table.globalTableVersion(),
        mapList(table.hasReplicas() ? table.replicas() : null, ReplicaDescriptionResult::from),
        RestoreSummaryResult.from(table.restoreSummary()),
        ArchivalSummaryResult.from(table.archivalSummary()),
        TableClassSummaryResult.from(table.tableClassSummary()),
        table.deletionProtectionEnabled(),
        OnDemandThroughputResult.from(table.onDemandThroughput()),
        SSEDescriptionResult.from(table.sseDescription()));
  }

  static <T, R> List<R> mapList(
      final List<T> values, final java.util.function.Function<T, R> mapper) {
    return values == null ? null : values.stream().map(mapper).toList();
  }

  static Date toDate(final Instant instant) {
    return instant == null ? null : Date.from(instant);
  }

  @JsonPropertyOrder({"attributeName", "attributeType"})
  public record AttributeDefinitionResult(String attributeName, String attributeType) {
    static AttributeDefinitionResult from(final AttributeDefinition definition) {
      return new AttributeDefinitionResult(
          definition.attributeName(), definition.attributeTypeAsString());
    }
  }

  @JsonPropertyOrder({"attributeName", "keyType"})
  public record KeySchemaElementResult(String attributeName, String keyType) {
    static KeySchemaElementResult from(final KeySchemaElement element) {
      return new KeySchemaElementResult(element.attributeName(), element.keyTypeAsString());
    }
  }

  @JsonPropertyOrder({
    "lastIncreaseDateTime",
    "lastDecreaseDateTime",
    "numberOfDecreasesToday",
    "readCapacityUnits",
    "writeCapacityUnits"
  })
  public record ProvisionedThroughputResult(
      Date lastIncreaseDateTime,
      Date lastDecreaseDateTime,
      Long numberOfDecreasesToday,
      Long readCapacityUnits,
      Long writeCapacityUnits) {
    static ProvisionedThroughputResult from(final ProvisionedThroughputDescription throughput) {
      if (throughput == null) {
        return null;
      }
      return new ProvisionedThroughputResult(
          toDate(throughput.lastIncreaseDateTime()),
          toDate(throughput.lastDecreaseDateTime()),
          throughput.numberOfDecreasesToday(),
          throughput.readCapacityUnits(),
          throughput.writeCapacityUnits());
    }
  }

  @JsonPropertyOrder({"billingMode", "lastUpdateToPayPerRequestDateTime"})
  public record BillingModeSummaryResult(
      String billingMode, Date lastUpdateToPayPerRequestDateTime) {
    static BillingModeSummaryResult from(final BillingModeSummary summary) {
      if (summary == null) {
        return null;
      }
      return new BillingModeSummaryResult(
          summary.billingModeAsString(), toDate(summary.lastUpdateToPayPerRequestDateTime()));
    }
  }

  @JsonPropertyOrder({"projectionType", "nonKeyAttributes"})
  public record ProjectionResult(String projectionType, List<String> nonKeyAttributes) {
    static ProjectionResult from(final Projection projection) {
      if (projection == null) {
        return null;
      }
      return new ProjectionResult(
          projection.projectionTypeAsString(),
          projection.hasNonKeyAttributes() ? projection.nonKeyAttributes() : null);
    }
  }

  @JsonPropertyOrder({
    "indexName",
    "keySchema",
    "projection",
    "indexSizeBytes",
    "itemCount",
    "indexArn"
  })
  public record LocalSecondaryIndexDescriptionResult(
      String indexName,
      List<KeySchemaElementResult> keySchema,
      ProjectionResult projection,
      Long indexSizeBytes,
      Long itemCount,
      String indexArn) {
    static LocalSecondaryIndexDescriptionResult from(final LocalSecondaryIndexDescription index) {
      return new LocalSecondaryIndexDescriptionResult(
          index.indexName(),
          mapList(index.hasKeySchema() ? index.keySchema() : null, KeySchemaElementResult::from),
          ProjectionResult.from(index.projection()),
          index.indexSizeBytes(),
          index.itemCount(),
          index.indexArn());
    }
  }

  @JsonPropertyOrder({"maxReadRequestUnits", "maxWriteRequestUnits"})
  public record OnDemandThroughputResult(Long maxReadRequestUnits, Long maxWriteRequestUnits) {
    static OnDemandThroughputResult from(final OnDemandThroughput throughput) {
      if (throughput == null) {
        return null;
      }
      return new OnDemandThroughputResult(
          throughput.maxReadRequestUnits(), throughput.maxWriteRequestUnits());
    }
  }

  @JsonPropertyOrder({"readUnitsPerSecond", "writeUnitsPerSecond", "status"})
  public record IndexWarmThroughputResult(
      Long readUnitsPerSecond, Long writeUnitsPerSecond, String status) {
    static IndexWarmThroughputResult from(
        final GlobalSecondaryIndexWarmThroughputDescription warmThroughput) {
      if (warmThroughput == null) {
        return null;
      }
      return new IndexWarmThroughputResult(
          warmThroughput.readUnitsPerSecond(),
          warmThroughput.writeUnitsPerSecond(),
          warmThroughput.statusAsString());
    }
  }

  @JsonPropertyOrder({
    "indexName",
    "keySchema",
    "projection",
    "indexStatus",
    "backfilling",
    "provisionedThroughput",
    "indexSizeBytes",
    "itemCount",
    "indexArn",
    "onDemandThroughput",
    "warmThroughput"
  })
  public record GlobalSecondaryIndexDescriptionResult(
      String indexName,
      List<KeySchemaElementResult> keySchema,
      ProjectionResult projection,
      String indexStatus,
      Boolean backfilling,
      ProvisionedThroughputResult provisionedThroughput,
      Long indexSizeBytes,
      Long itemCount,
      String indexArn,
      OnDemandThroughputResult onDemandThroughput,
      IndexWarmThroughputResult warmThroughput) {
    static GlobalSecondaryIndexDescriptionResult from(final GlobalSecondaryIndexDescription index) {
      return new GlobalSecondaryIndexDescriptionResult(
          index.indexName(),
          mapList(index.hasKeySchema() ? index.keySchema() : null, KeySchemaElementResult::from),
          ProjectionResult.from(index.projection()),
          index.indexStatusAsString(),
          index.backfilling(),
          ProvisionedThroughputResult.from(index.provisionedThroughput()),
          index.indexSizeBytes(),
          index.itemCount(),
          index.indexArn(),
          OnDemandThroughputResult.from(index.onDemandThroughput()),
          IndexWarmThroughputResult.from(index.warmThroughput()));
    }
  }

  @JsonPropertyOrder({"streamEnabled", "streamViewType"})
  public record StreamSpecificationResult(Boolean streamEnabled, String streamViewType) {
    static StreamSpecificationResult from(final StreamSpecification spec) {
      if (spec == null) {
        return null;
      }
      return new StreamSpecificationResult(spec.streamEnabled(), spec.streamViewTypeAsString());
    }
  }

  @JsonPropertyOrder({"readCapacityUnits"})
  public record ProvisionedThroughputOverrideResult(Long readCapacityUnits) {
    static ProvisionedThroughputOverrideResult from(final ProvisionedThroughputOverride override) {
      if (override == null) {
        return null;
      }
      return new ProvisionedThroughputOverrideResult(override.readCapacityUnits());
    }
  }

  @JsonPropertyOrder({"maxReadRequestUnits"})
  public record OnDemandThroughputOverrideResult(Long maxReadRequestUnits) {
    static OnDemandThroughputOverrideResult from(final OnDemandThroughputOverride override) {
      if (override == null) {
        return null;
      }
      return new OnDemandThroughputOverrideResult(override.maxReadRequestUnits());
    }
  }

  @JsonPropertyOrder({"readUnitsPerSecond", "writeUnitsPerSecond", "status"})
  public record TableWarmThroughputResult(
      Long readUnitsPerSecond, Long writeUnitsPerSecond, String status) {
    static TableWarmThroughputResult from(final TableWarmThroughputDescription warmThroughput) {
      if (warmThroughput == null) {
        return null;
      }
      return new TableWarmThroughputResult(
          warmThroughput.readUnitsPerSecond(),
          warmThroughput.writeUnitsPerSecond(),
          warmThroughput.statusAsString());
    }
  }

  @JsonPropertyOrder({"tableClass", "lastUpdateDateTime"})
  public record TableClassSummaryResult(String tableClass, Date lastUpdateDateTime) {
    static TableClassSummaryResult from(final TableClassSummary summary) {
      if (summary == null) {
        return null;
      }
      return new TableClassSummaryResult(
          summary.tableClassAsString(), toDate(summary.lastUpdateDateTime()));
    }
  }

  @JsonPropertyOrder({
    "indexName",
    "provisionedThroughputOverride",
    "onDemandThroughputOverride",
    "warmThroughput"
  })
  public record ReplicaGlobalSecondaryIndexDescriptionResult(
      String indexName,
      ProvisionedThroughputOverrideResult provisionedThroughputOverride,
      OnDemandThroughputOverrideResult onDemandThroughputOverride,
      IndexWarmThroughputResult warmThroughput) {
    static ReplicaGlobalSecondaryIndexDescriptionResult from(
        final ReplicaGlobalSecondaryIndexDescription index) {
      return new ReplicaGlobalSecondaryIndexDescriptionResult(
          index.indexName(),
          ProvisionedThroughputOverrideResult.from(index.provisionedThroughputOverride()),
          OnDemandThroughputOverrideResult.from(index.onDemandThroughputOverride()),
          IndexWarmThroughputResult.from(index.warmThroughput()));
    }
  }

  @JsonPropertyOrder({
    "regionName",
    "replicaStatus",
    "replicaArn",
    "replicaStatusDescription",
    "replicaStatusPercentProgress",
    "kmsmasterKeyId",
    "provisionedThroughputOverride",
    "onDemandThroughputOverride",
    "warmThroughput",
    "globalSecondaryIndexes",
    "replicaInaccessibleDateTime",
    "replicaTableClassSummary"
  })
  public record ReplicaDescriptionResult(
      String regionName,
      String replicaStatus,
      String replicaArn,
      String replicaStatusDescription,
      String replicaStatusPercentProgress,
      String kmsmasterKeyId,
      ProvisionedThroughputOverrideResult provisionedThroughputOverride,
      OnDemandThroughputOverrideResult onDemandThroughputOverride,
      TableWarmThroughputResult warmThroughput,
      List<ReplicaGlobalSecondaryIndexDescriptionResult> globalSecondaryIndexes,
      Date replicaInaccessibleDateTime,
      TableClassSummaryResult replicaTableClassSummary) {
    static ReplicaDescriptionResult from(final ReplicaDescription replica) {
      return new ReplicaDescriptionResult(
          replica.regionName(),
          replica.replicaStatusAsString(),
          replica.replicaArn(),
          replica.replicaStatusDescription(),
          replica.replicaStatusPercentProgress(),
          replica.kmsMasterKeyId(),
          ProvisionedThroughputOverrideResult.from(replica.provisionedThroughputOverride()),
          OnDemandThroughputOverrideResult.from(replica.onDemandThroughputOverride()),
          TableWarmThroughputResult.from(replica.warmThroughput()),
          mapList(
              replica.hasGlobalSecondaryIndexes() ? replica.globalSecondaryIndexes() : null,
              ReplicaGlobalSecondaryIndexDescriptionResult::from),
          toDate(replica.replicaInaccessibleDateTime()),
          TableClassSummaryResult.from(replica.replicaTableClassSummary()));
    }
  }

  @JsonPropertyOrder({"sourceBackupArn", "sourceTableArn", "restoreDateTime", "restoreInProgress"})
  public record RestoreSummaryResult(
      String sourceBackupArn,
      String sourceTableArn,
      Date restoreDateTime,
      Boolean restoreInProgress) {
    static RestoreSummaryResult from(final RestoreSummary summary) {
      if (summary == null) {
        return null;
      }
      return new RestoreSummaryResult(
          summary.sourceBackupArn(),
          summary.sourceTableArn(),
          toDate(summary.restoreDateTime()),
          summary.restoreInProgress());
    }
  }

  @JsonPropertyOrder({"archivalDateTime", "archivalReason", "archivalBackupArn"})
  public record ArchivalSummaryResult(
      Date archivalDateTime, String archivalReason, String archivalBackupArn) {
    static ArchivalSummaryResult from(final ArchivalSummary summary) {
      if (summary == null) {
        return null;
      }
      return new ArchivalSummaryResult(
          toDate(summary.archivalDateTime()),
          summary.archivalReason(),
          summary.archivalBackupArn());
    }
  }

  @JsonPropertyOrder({"status", "ssetype", "kmsmasterKeyArn", "inaccessibleEncryptionDateTime"})
  public record SSEDescriptionResult(
      String status, String ssetype, String kmsmasterKeyArn, Date inaccessibleEncryptionDateTime) {
    static SSEDescriptionResult from(final SSEDescription description) {
      if (description == null) {
        return null;
      }
      return new SSEDescriptionResult(
          description.statusAsString(),
          description.sseTypeAsString(),
          description.kmsMasterKeyArn(),
          toDate(description.inaccessibleEncryptionDateTime()));
    }
  }
}
