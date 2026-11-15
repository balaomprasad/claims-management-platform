# ADR 0003: Currency-aware liability reporting

**Status:** Accepted

**Context.** Claims are filed in six APAC markets, each with its own local currency (AUD, SGD, HKD, INR, MYR, THB). The manager dashboard must report exposure and liability.

**Decision.**
- Each policy carries a market and a currency. Claims copy both at submission.
- Every monetary metric is grouped by currency:
  - **estimated exposure**: estimated loss on claims awaiting a decision
  - **approved liability**: approved amounts awaiting settlement
  - **settled amount**
- Amounts in different currencies are never summed. There is no configured, auditable exchange-rate source.
- The dashboard response includes the metric definitions.

**Consequences.**
- Figures are always correct, but there's no single "total liability" number.
- A reporting-currency total can be added later with a dated FX-rate table, applied explicitly and labelled with the rate date.
