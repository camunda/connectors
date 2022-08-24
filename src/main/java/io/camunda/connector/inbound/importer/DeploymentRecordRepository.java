package io.camunda.connector.inbound.importer;

import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface DeploymentRecordRepository extends
    ElasticsearchRepository<DeploymentRecord, String> {

  Iterable<DeploymentRecord> findAllByPositionGreaterThan(long position, Sort sort);
}
