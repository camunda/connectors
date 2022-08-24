package io.camunda.connector.inbound.importer;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ImporterPositionRepository extends ElasticsearchRepository<ImporterPosition, String> {
}
