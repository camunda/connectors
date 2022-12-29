# Camunda out-of-the-box connectors

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/available-connectors-overview/)

## Test locally

Run unit tests

```bash
mvn clean verify
```

### Test with local runtime

Use the [Camunda Connector Runtime](https://github.com/camunda-community-hub/spring-zeebe/tree/master/connector-runtime#building-connector-runtime-bundles) to run your function as a local Java application.

In your IDE you can also simply navigate to the
[LocalContainerRuntime](../bundle/mvn/default-bundle/src/test/java/io/camunda/connector/bundle/LocalConnectorRuntime.java)
class in test scope of the `default-bundle` module and run it via your IDE.


### :lock: Test as local Google Cloud Function

> **Warning**
> This is Camunda-internal only. The Maven profile `cloud-function` accesses an internal artifact.

Build as Google Cloud Function

```bash
mvn function:run -Pcloud-function
```

See also the [:lock:Camunda Cloud Connector Run-Time](https://github.com/camunda/connector-runtime-cloud) on how your function
is run as a Google Cloud Function.

## Element Template

The element templates can be found in the `element-templates` directory of each connector.

Example: [aws-lambda/element-templates](aws-lambda/element-templates/aws-lambda-connector.json)

## Development guidelines

### Create a new Connector

To create a new Connector, simply use the [script](../add_new_connector.sh) or generate a new
project from the [Maven archetype](../connector-archetype-internal).

Execute from the repository root directory:
```shell
chmod +x add_new_connector.sh
./add_new_connector.sh ${YOUR_CONNECTOR_NAME}
```
Substitute `${YOUR_CONNECTOR_NAME}` with the name of your new Connector.
**Please provide the name in a short format:** e.g. use simply `slack`, **not** `connector-slack`.

The script will create a Maven sub-module in the `connectors` directory. You will likely
need to import the project in your IDE manually.

### Add new Connector to the bundle

As a next step, please include your Connector in the connectors bundle.
- Add it to the `dependencyManagement` section of [bundle parent POM](../bundle/mvn/pom.xml)
- Add it to the `dependencies` section of [default bundle POM](../bundle/mvn/default-bundle/pom.xml)
