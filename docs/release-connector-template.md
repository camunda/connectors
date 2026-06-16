# Release a Connector Template

This how-to documents how to ship a new or updated connector template with C8 SaaS.

## Background

* Features that shall not be available to the public must be hidden behind a feature toggle

## npm package

* An npm package includes all element templates.
* The npm package can be published with a GitHub Action within the regular connectors release.
* The npm package is built with all element templates inside this repository.
