# Benchmark

## Methodology

1. Parse each fixture with every target parser
2. Feed the parsed output to an LLM with a golden-set question — the only variable is the parser's output
3. Check if the LLM's answer matches the verified consensus answer
4. Score = % of correct answers

> **Note:** the Public features below are a representative subset for transparency. The full golden set — including Private features — remains private and continues to grow.

## Public features

Fixture files are in [`fixtures/`](fixtures/).

Description includes structural tags used as inputs for future strategy selection:
- **header**: `simple` (single-row header) / `multi-row-merged` (header spans multiple merged rows) / `non-header-top` (top rows are a banner or metadata, not the header)
- **tables**: `single` (one table per sheet) / `multi` (two or more tables per sheet — stacked or side-by-side)

| File | Language | Sheets | Rows | Cols | Merges | Description |
|------|----------|--------|------|------|--------|-------------|
| R01_invoice.xlsx | EN | 1 | 17 | 4 | 1 | Invoice with line items<br>header: non-header-top / tables: single |
| R02_budget.xlsx | EN | 1 | 15 | 4 | 3 | Budget report with categories<br>header: simple / tables: single |
| R03_sales.xlsx | EN | 3 | 213 | 25 | 1 | Sales report with summary<br>header: simple / tables: single |
| R04_employee.xlsx | EN | 1 | 203 | 25 | 3 | Employee roster with multi-level headers<br>header: multi-row-merged / tables: single |
| R05_financial.xlsx | EN | 2 | 22 | 5 | 2 | Income statement + balance sheet<br>header: simple / tables: single |
| R06_product-catalog.xlsx | EN | 1 | 502 | 8 | 1 | Product catalog (500 items)<br>header: simple / tables: single |
| R07_quarterly.xlsx | EN | 3 | 60 | 10 | 3 | Quarterly financial (3 sheets)<br>header: simple / tables: single |
| R08_multi-dept.xlsx | EN | 6 | 172 | 22 | 18 | Multi-department report (6 sheets)<br>header: multi-row-merged / tables: multi |
| R09_schedule.xlsx | EN | 1 | 9 | 6 | 3 | Weekly schedule with merged cells<br>header: simple / tables: single |
| R10_time-tracking.xlsx | EN | 1 | 152 | 30 | 5 | Time tracking (150 employees)<br>header: multi-row-merged / tables: single |

## Submit your golden set

Have a tricky Excel file? Submit your file, a question, and the expected answer. Submitted features and Q&A will be **fully published** in the benchmark.

Contact: [GitHub](https://github.com/Hyune-c) · [LinkedIn](https://www.linkedin.com/in/b30b971a0/)
