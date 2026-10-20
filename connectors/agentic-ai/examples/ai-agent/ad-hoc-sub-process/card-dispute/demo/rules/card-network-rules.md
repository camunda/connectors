# Card network dispute rules (synthetic excerpt)

Synthetic demo content. It stands in for a card network's published dispute rules.

## duplicate: CN-DUP-01 Duplicate processing

- **Eligible when**: the same card was charged the same amount by the same merchant more than once for one purchase.
- **Time limit**: the cardholder must dispute within 120 days of the second charge.
- **Evidence**: both transaction references and the merchant name.

## not_received: CN-NR-02 Goods or services not received

- **Eligible when**: the merchant did not deliver by the promised date and did not answer the cardholder.
- **Time limit**: 120 days from the promised delivery date.
- **Evidence**: the order confirmation and the cardholder's contact attempts.

## fraud: CN-FR-03 Unauthorized transaction

- **Eligible when**: the cardholder states they did not make or authorize the charge.
- **Time limit**: 120 days from the charge.
- **Evidence**: the cardholder's signed statement and the fraud risk opinion.

## other: CN-GEN-00 General dispute

- **Eligible when**: none of the categories above fits. Requires merchant contact before filing.
- **Time limit**: 120 days.
- **Evidence**: the cardholder's statement.
