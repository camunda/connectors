# Camunda 8 Operate Connector

The **Operate Connector** allows you to query data from [Camunda Operate](https://camunda.com/platform/operate/).
This Connector can be used to call Operate deployments both in Camunda 8 SaaS and Self-Managed clusters.

This Connector reuses the base implementation of [HTTP JSON Connector](../http-json) by providing a compatible element template.

## Supported operations

This Connector supports the following operations:
- Get by key
- Search

The operations above can be applied to all entities in the Operate API:
- Process definitions
- Process instances
- Incidents
- Flownode instances
- Variables

## Unsupported operations

The following operations are currently not supported:
- Delete process instance
- Get process definition XML

Note: basic authentication (username & password) is not supported.

## Operate API documentation

Please refer to [Operate API documentation](https://docs.camunda.io/docs/apis-clients/operate-api/) for a complete overview of API entities and endpoints.