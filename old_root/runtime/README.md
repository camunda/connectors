# Camunda 8 Connector Runtime

This project provides a base Docker image including the [Spring Zeebe Connector runtime](https://github.com/camunda-community-hub/spring-zeebe/tree/master/connector-runtime). 
The image starts the job worker runtime with all `jar` files provided in the `/opt/app` directory as classpath.

To use the image at least one Connector has to be added to the classpath. We recommend to provide jars with all dependencies bundled.

> :warning: As all Connectors share a single classpath, it can happen that
> different versions of the same dependency are available which can lead to
> conflicts. To prevent this, common dependencies like `jackson` can be shaded and
> relocated inside the connector jar.

Example adding a Connector JAR by extending the image

```dockerfile
FROM camunda/connectors:0.3.1

ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-http-json/0.11.0/connector-http-json-0.11.0-with-dependencies.jar /opt/app/
```

Example adding a Connector JAR by using volumes

```bash
docker run --rm --name=connectors -d -v $PWD/connector.jar:/opt/app/ camunda/connectors:0.3.1
```
