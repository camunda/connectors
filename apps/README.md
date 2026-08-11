# Applications

This directory contains all applications delivered by this repository, i.e. every module we build a
Docker image from.

| Module                                                                     | Artifact                       | Docker image                    |
|----------------------------------------------------------------------------|--------------------------------|---------------------------------|
| [`connector-runtime-application`](connector-runtime-application)           | `connector-runtime-application`| `camunda/connectors`            |
| [`bundle/default-bundle`](bundle/default-bundle)                           | `connector-runtime-bundle`     | `camunda/connectors-bundle`     |
| [`bundle/camunda-saas-bundle`](bundle/camunda-saas-bundle)                 | `connector-runtime-bundle-saas`| `camunda/connectors-bundle-saas`|

`connector-runtime-application` is the plain runtime without any Connector on the classpath. The
[bundles](bundle) build on top of it and additionally ship the out-of-the-box Connectors.

The runtime libraries these applications are built from live in
[`connector-runtime`](../connector-runtime).
