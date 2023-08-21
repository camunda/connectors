# Salesforce Connectors

The Salesforce Connectors allow you to:

* Query data from Salesforce using [SOQL](https://developer.salesforce.com/docs/atlas.en-us.soql_sosl.meta/soql_sosl/sforce_api_calls_soql.htm)
* Interact with [sObjects directly](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/using_resources_working_with_records.htm)

This Connector reuses the base implementation of [REST Connector](./../http/rest) by providing a compatible element template.

## Supported Operations

* any kind of SOQL query
* CRUD operations with sObjects

## Unsupported Operations

All other APIs mentioned [here](https://developer.salesforce.com/docs/apis).

