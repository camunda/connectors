package io.camunda.connector.inbound.importer;

import java.util.List;

public record DeploymentRecordValue(List<ProcessMetadata> processesMetadata,
                                    List<Resource> resources) {

}
