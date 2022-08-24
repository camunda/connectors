package io.camunda.connector.inbound.importer;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "zeebe-record-deployment", createIndex = false)
public record DeploymentRecord(@Id String id, String intent, long key, int partitionId,
                               long position, long sourceRecordPosition,
                               @Field(type = FieldType.Object, includeInParent = true) DeploymentRecordValue value) {

}
