# Connector Template Versioning

Connector Templates are based on the concept of element templates, which have their own [lifecycle](https://github.com/bpmn-io/element-templates/blob/main/docs/LIFE_CYCLE.md) in terms of how they can be applied, upgraded and deprecated. Providing a `version` allows template authors to add new functionality while keeping existing templates in place and allowing users to take time to update or migrate their models to
a newer version of an element template.

## Why

We version our connector templates in order to allow our users to gracefully update them.

## How

Versioning happens by attaching a `version` attribute to an element template:

```json
{
  "name": "My Template",
  "version": 1
}
```

Versions, if provided, must be an integer. No version is always the first version.

## When to bump?

Version bumps are required whenever non-cosmetic updates are shipped with the template.

This includes adding new properties, changing the template icon, or changing default property bindings.

## File schema for released templates

Element templates are versioned. The latest version of an element template is in the root of the element-templates directory. Older versions have adjusted names and are inside the `versioned` subfolder.
For releasing the template with the [bundle](../bundle) / integrating it into the SaaS Web Modeler (see [Release a Connector Template](./release-connector-template.md))
we explicitly encode the template version in the name:

```
slack-connector.json # latest version, e.g. version 2
versioned/slack-connector-1.json # version 1
```

This is taken care of automatically during the bundle release build.
When integrating templates into the Web Modeler we must carry out the renaming manually.
