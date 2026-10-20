#!/usr/bin/env python3
"""Calculate filing windows for the synthetic chargeback demo."""

import argparse
from datetime import date, timedelta
import hashlib
import json
from pathlib import Path
import re
import sys


def parse_date(value):
    if not isinstance(value, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        raise ValueError("Dates must use YYYY-MM-DD.")
    try:
        return date.fromisoformat(value)
    except ValueError as error:
        raise ValueError("Date does not exist: " + value) from error


def calculate(charge_dates, reason_code, filed_on, promised_delivery_date=None):
    if not charge_dates:
        raise ValueError("Supply at least one charge date.")
    dates = sorted(parse_date(value) for value in charge_dates)
    filed = parse_date(filed_on)
    if reason_code == "CN-DUP-01":
        if len(dates) < 2:
            raise ValueError("CN-DUP-01 requires at least two reconciled charge dates.")
        anchor = dates[-1]
        basis = "last_duplicate_charge"
    elif reason_code == "CN-FR-03":
        if len(dates) != 1:
            raise ValueError("CN-FR-03 requires exactly one disputed charge date.")
        anchor = dates[0]
        basis = "disputed_charge"
    elif reason_code == "CN-NR-02":
        if promised_delivery_date is None:
            raise ValueError("CN-NR-02 requires --promised-delivery-date.")
        anchor = parse_date(promised_delivery_date)
        basis = "promised_delivery"
    elif reason_code == "CN-GEN-00":
        raise ValueError("CN-GEN-00 requires manual review because its starting date is undefined.")
    else:
        raise ValueError("Unsupported reason code: " + str(reason_code))
    if promised_delivery_date is not None and reason_code != "CN-NR-02":
        raise ValueError("--promised-delivery-date applies only to CN-NR-02.")
    if filed < anchor or filed < dates[-1]:
        raise ValueError("Filing date cannot precede the starting date or a supplied charge.")
    try:
        deadline = anchor + timedelta(days=120)
    except OverflowError as error:
        raise ValueError("Filing deadline exceeds the supported date range.") from error
    return {
        "skill": "chargeback-deadline",
        "reasonCode": reason_code,
        "chargeDates": [value.isoformat() for value in dates],
        "timeLimitDays": 120,
        "anchorBasis": basis,
        "anchorDate": anchor.isoformat(),
        "lastChargeDate": dates[-1].isoformat(),
        "filingDeadline": deadline.isoformat(),
        "filedOn": filed.isoformat(),
        "eligible": filed <= deadline,
        "eligibilityScope": "filing_window_only",
        "daysBetweenCharges": (dates[-1] - dates[0]).days if len(dates) >= 2 else None,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--charge-date", action="append", required=True)
    parser.add_argument("--reason-code", required=True)
    parser.add_argument("--filed-on", required=True)
    parser.add_argument("--promised-delivery-date")
    args = parser.parse_args(argv)
    try:
        result = calculate(args.charge_date, args.reason_code, args.filed_on, args.promised_delivery_date)
        result["scriptSha256"] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    except (ValueError, OSError) as error:
        print(json.dumps({"error": str(error)}), file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
