# Template text guidelines

The agentic AI templates have hundreds of properties, and the same concept often appears in several
templates (for example, the model parameters of every provider). Write the help text in the
`@TemplateProperty` annotations so that the same field reads the same everywhere:

- **Pick the help mechanism by what the text is**, as described in the repo-root
  [`AGENTS.md`](../../../AGENTS.md#element-templates):
  - `tooltip`: explanations and links to the documentation.
  - `placeholder`: an example value, without a trailing period. Text inputs only; dropdowns and
    checkboxes have no placeholder.
  - `description`: value constraints, `Note:`/`Warning:` markers, and critical guidance only.
  - `label`: a short noun phrase, without a trailing period.
- **Reuse existing wording.** Before writing help text for a field, search the module for the same
  label and copy its `tooltip` and `description`, adapting only the provider name. The same fact
  goes in the same field everywhere, not a `tooltip` in one template and a `description` in another.
- **Voice**: address the user directly in active voice and present tense ("Specify the model ID.",
  not "The model ID should be specified.").
- **No Latin abbreviations**: write "for example", "that is", and "and so on" instead of `e.g.`,
  `i.e.`, and `etc.`.
- **End every `tooltip` and `description` with a period.**
- **Line breaks**: use `<br>`, not `<br/>`.
- **Documentation links** pin the current released minor, never an unversioned or `/next/` URL. See
  the repo-root [`AGENTS.md`](../../../AGENTS.md#element-templates) for the rationale.

The [element template validator](../../../element-template-generator/validator/README.md#help-text-rules)
enforces the last four rules in CI, and also flags repeated words such as "to to". Existing
findings are suppressed in each module's `element-template-suppressions.json`. Delete an entry only
when all findings it covers are fixed; template-wide entries may cover several properties or groups.
The validator fails on entries that no longer suppress any finding.

Changing only help text does not need a template version bump.
