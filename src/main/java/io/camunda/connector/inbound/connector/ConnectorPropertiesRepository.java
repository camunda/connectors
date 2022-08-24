package io.camunda.connector.inbound.connector;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ConnectorPropertiesRepository extends
    ElasticsearchRepository<ConnectorProperties, String> {
}
