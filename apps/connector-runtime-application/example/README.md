# Connectors Bundle

This module is an example of bundling a custom set of Connectors with Connector Runtime.

This bundle is built on top of our [Connectors base image](https://hub.docker.com/r/camunda/connectors/) that includes pre-packaged runtime without any Connectors.
You can find the Dockerfile for the base image in the [connector-runtime-application](../Dockerfile) module.

## Run the bundle

```shell
docker run --rm -it $(docker build -q .)
```
This will run a container built from the Dockerfile in this repo.

## Modify the bundle

Add your custom connectors to the bundle similarly to 

> :warning: As all Connectors share a single classpath, it can happen that
> different versions of the same dependency are available which can lead to
> conflicts. To prevent this, common dependencies like `jackson` can be shaded and
> relocated inside the connector jar.
>
> An example of such relocation with`maven-shade-plugin` can be found in the
> [Camunda 8 out-of-the-box Connectors repository](https://github.com/camunda/connectors-bundle/blob/cb925c1c5bdab9f55b77d2378be32116dec6f010/connectors/pom.xml#L83).

Please refer to the base [Connector Runtime image documentation](../README.md)
for further information.

## Consider bundling with Maven

Another way to bundle Connectors with Connector Runtime is using Maven.
In this case all version conflicts will be resolved by Maven,
so relocating the common dependencies becomes irrelevant.

The [Connectors Bundle project](https://github.com/camunda/connectors-bundle)
is an example of Maven bundle. It contains all available out-of-the-box 
Connectors provided by Camunda 8.
