# Camunda 8 out-of-the-box Connectors

[![CI](https://github.com/camunda/connectors-bundle/actions/workflows/DEPLOY.yaml/badge.svg)](https://github.com/camunda/connectors-bundle/actions/workflows/DEPLOY.yml)

This repository manages the [out-of-the-box Connectors](./connectors) provided by Camunda,
as well as the [Connector Runtime](./connector-runtime) implementation.
For more information on those Connectors, refer to the
[Camunda 8 documentation](https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/available-connectors-overview/).

Additionally, this repository manages the [Docker images](./bundle) of the out-of-the-box Connectors for Camunda 8, bundled with a runtime.

# License

This is a multi-module project with different licenses applied to different modules.

* [Connector Runtime](connector-runtime) and all its submodules are licensed under [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0).
* Most of the [Out-of-the-Box Connectors](connectors) are licensed under [Camunda Platform Self-Managed Free Edition](https://camunda.com/legal/terms/cloud-terms-and-conditions/camunda-cloud-self-managed-free-edition-terms/) license.
* However, [REST Connector](connectors/http-json) is licensed under [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0).
* The Connectors Bundle Docker image contains Connectors licensed under [Camunda Platform Self-Managed Free Edition](https://camunda.com/legal/terms/cloud-terms-and-conditions/camunda-cloud-self-managed-free-edition-terms/) license.

When in doubt, refer to the `LICENSE` file in the respective module.
