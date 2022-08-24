package io.camunda.connector.inbound.importer;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Setting;

@Document(indexName = "connector-importer-position")
@Setting(replicas = 0)
public record ImporterPosition(@Id String id, long position) {

}
