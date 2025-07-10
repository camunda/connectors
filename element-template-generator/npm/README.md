# NPM Package to provide all element templates to the web-modeler

This package is automatically generated from the `element-templates` sub-directories. It provides all element templates as an array of objects that can be used in the web-modeler.

## Development

### Build and test locally

To build this package, run:

- `npm install`
- `npm run pre-build`

To test this package with web-modeler locally:

- Run `npm` link in `connectors/element-template-generator/npm` folder
- Start the web-modeler
- add this code to the babel-loader rule in the webpack config.

```
include: [
            path.resolve(__dirname, 'src'),
            path.resolve(__dirname, 'node_modules/@camunda/connectors-element-templates')
          ],
```

- run `npm link @camunda/connectors-element-templates` in the `web-modeler/webapp` folder

### Release a new version

Run the github action "PUBLISH_NPM_PACKAGE". If publishing without the github action make sure to run the pre-build script and tests before publishing!
