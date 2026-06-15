# Camunda Orchestration Cluster API Connector

The **Orchestration Cluster API Connector** allows you to query data from the [Camunda 8 Orchestration Cluster REST API (v2)](https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/).
This Connector can be used to call Camunda 8 clusters in both SaaS and Self-Managed deployments and is the replacement for the deprecated [Operate Connector](../operate).

This Connector reuses the base implementation of the [HTTP JSON Connector](../http-json) by providing a compatible element template.

## Supported operations

This Connector is read-only and supports the following operations:
- Get by key (GET `/{entity}/{key}`)
- Search (POST `/{entity}/search`)

The operations above can be applied to the following Orchestration Cluster API v2 entities:
- Process instances
- Process definitions
- Element instances
- Incidents
- Variables
- User tasks
- Jobs
- Decision instances
- Decision definitions
- Decision requirements
- Batch operations
- Batch operation items (search only)
- Message subscriptions (search only)
- Correlated message subscriptions (search only)
- Audit logs
- Authorizations
- Groups
- Roles
- Tenants
- Mapping rules

## Unsupported operations

This Connector is read-only by design. State-changing operations (create, update, delete, cancel, migrate, modify, resolve, etc.) are not exposed. Basic authentication (username & password) is not supported; OAuth 2.0 client credentials are required.

## Requirements

- Camunda 8.9 or later
- OAuth 2.0 client credentials with permission to call the Orchestration Cluster API

## API documentation

Refer to the [Orchestration Cluster API REST documentation](https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/) for the full list of entities, endpoints, filter fields, and response shapes.
