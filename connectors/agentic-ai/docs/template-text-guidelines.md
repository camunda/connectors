# Template text guidelines

Template text is for low-code developers and AI coding agents. English is often not their first
language. Write the text to meet these goals:

- The user can configure the template without opening the documentation.
- The documentation covers expert use cases and fine-tuning, and the `tooltip` links to it.
- Each concept uses the same term as docs.camunda.io uses for that concept.

To decide where a fact goes, ask one question: does the user need this fact to set a correct value?
If yes, put the fact in the template. If no, put it in the documentation and link to it.

The agentic AI templates have hundreds of properties, and the same concept often appears in several
templates (for example, the model parameters of every provider). Write the help text in the
`@TemplateProperty` annotations so that the same field reads the same everywhere:

- Pick the help mechanism by what the text is, as described in the repo-root
  [`AGENTS.md`](../../../AGENTS.md#element-templates):
  - `tooltip`: explanations and links to the documentation. Aim for at most 200 characters of prose
    before the link. Split longer explanations with `<br><br>`.
  - `placeholder`: the default value of the field, or an example value if the field has no default.
    No trailing period. Text inputs only. Dropdowns and checkboxes have no placeholder. Keep it under
    about 40 characters so the input does not truncate it.
  - `description`: value constraints, `Note:`/`Warning:` markers, and critical guidance only. It is
    always visible beneath the field, so keep it to one short sentence. The validator caps it at 100
    visible characters (HTML stripped); move longer detail into the `tooltip` or the documentation.
  - `label`: a short noun phrase, without a trailing period, under about 30 characters.
- Reuse existing wording. Before writing help text for a field, search the module for the same
  label and reuse the wording of its shared explanation. The same fact goes in the same field
  everywhere, not a `tooltip` in one template and a `description` in another. Keep provider- and
  model-specific facts specific: value ranges and documentation links often differ for the same label
  (for example, Temperature is 0 to 2 for OpenAI but 0 to 1 for Anthropic), so take them from the
  provider's API, not from another provider's field.
- Voice: address the user directly in active voice and present tense ("Specify the model ID.",
  not "The model ID should be specified.").
- No Latin abbreviations: write "for example", "that is", and "and so on" instead of `e.g.`,
  `i.e.`, and `etc.`.
- End every `tooltip` and `description` with a period.
- Line breaks: use `<br>`, not `<br/>`.
- Documentation links pin the current released minor, never an unversioned or `/next/` URL. See
  the repo-root [`AGENTS.md`](../../../AGENTS.md#element-templates) for the rationale. Name the target
  in the link text so the user knows before clicking whether it leaves Camunda: use `documentation`
  for a Camunda page and name the provider for a third-party page (for example `Anthropic
  documentation`, `OpenAI documentation`). Use one sentence frame: `See the <a
  href="...">Anthropic documentation</a>.` The period is outside the link.

The [element template validator](../../../element-template-generator/validator/README.md#help-text-rules)
enforces the last four rules and the 100-character `description` cap in CI, and also flags repeated
words such as "to to". Existing findings are suppressed in each module's
`element-template-suppressions.json`. Delete an entry only when all findings it covers are fixed.
Template-wide entries can cover several properties or groups. The validator fails on entries that no
longer suppress any finding.

Changing only help text does not need a template version bump.
