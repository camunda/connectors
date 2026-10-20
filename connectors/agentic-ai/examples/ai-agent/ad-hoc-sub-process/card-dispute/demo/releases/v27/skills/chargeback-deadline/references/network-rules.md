# Card network dispute rules

Synthetic demo rules, not a real network's legal or operational rules.
Source: the v19 demo's `rules/card-network-rules.md`.
This skill replaces that file's mocked HTTP delivery.

## CN-DUP-01: duplicate processing

- The same card has multiple equal charges from one merchant for one purchase.
- Filing window: 120 calendar days after the last matched duplicate charge.
- Evidence: both transaction references, merchant name, and the cardholder's single-purchase claim.
- For exactly two charges, the last charge is the second charge.

The script takes only reconciled duplicate charge dates for this reason code.
It requires at least two dates.
Dates alone do not establish that the purchase was a duplicate.

## CN-NR-02: goods or services not received

- The merchant did not deliver by the promised date and did not answer the cardholder.
- Filing window: 120 calendar days after the promised delivery date.
- Evidence: order confirmation and the cardholder's contact attempts.

Supply `--promised-delivery-date` for this reason code.
Do not substitute a charge date for the promised delivery date.

## CN-FR-03: unauthorized transaction

- The cardholder states that they did not make or authorize the charge.
- Filing window: 120 calendar days after the disputed charge.
- Evidence: signed cardholder statement and the fraud risk opinion.

Supply exactly one disputed charge date.
The fraud opinion remains separate evidence, not proof of authorization.

## CN-GEN-00: general dispute

- No category above fits, and the cardholder must contact the merchant before filing.
- Filing window: 120 days.
- Evidence: cardholder statement and merchant contact.

The source excerpt does not define this window's starting date.
The script rejects this reason code and requires manual review.
Do not invent a starting date.

## Calculation conventions

These conventions are author input for the synthetic demo.
They do not add real network requirements.

- Dates use `YYYY-MM-DD`, without times or time zones.
- Add 120 calendar days to the applicable starting date.
- Filing on the deadline counts as within the window.
- Filing before the starting date is invalid input.
- `eligible` describes filing-window eligibility only.
- Final approval also requires evidence, reconciliation, fraud assessment, and the linked policy.

Reference case: matched charges on 2026-09-14 and 2026-09-15, filed on 2026-10-20, reason `CN-DUP-01`.
Expected deadline: 2027-01-13.
