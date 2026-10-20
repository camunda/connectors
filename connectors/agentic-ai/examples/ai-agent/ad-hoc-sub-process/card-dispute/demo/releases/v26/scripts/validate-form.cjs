const fs = require('node:fs');
const Ajv = require('ajv');
const addErrors = require('ajv-errors');
const schema = require('@bpmn-io/form-json-schema/resources/schema.json');

const ajv = new Ajv({ allErrors: true, strict: false });
addErrors(ajv);
const validate = ajv.compile(schema);

function checkComponents(components) {
  for (const component of components) {
    if ('value' in component || component.type === 'submit' ||
        (component.type === 'button' && 'key' in component)) {
      throw new Error(`Invalid component shape: ${component.id}`);
    }
    checkComponents(component.components || []);
  }
}

if (process.argv.length < 3) {
  throw new Error('Usage: node validate-form.cjs FORM...');
}
for (const file of process.argv.slice(2)) {
  const form = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (!validate(form)) {
    throw new Error(`${file}: ${JSON.stringify(validate.errors, null, 2)}`);
  }
  checkComponents(form.components);
  console.log(`${file}: valid`);
}
