# Benchmark

## Methodology

1. Parse each fixture with every target parser
2. Feed the parsed output to an LLM with a golden-set question — the only variable is the parser's output
3. Check if the LLM's answer matches the verified consensus answer
4. Score = % of correct answers

> **Note:** the published fixtures below are a representative subset for transparency. The full golden set remains private and continues to grow.

## Published fixtures

Fixture files are in [`fixtures/`](fixtures/).

| File | Language | Sheets | Rows | Cols | Merges | Description |
|------|----------|--------|------|------|--------|-------------|
| R01_invoice.xlsx | EN | 1 | 17 | 4 | 1 | Invoice with line items |
| R02_budget.xlsx | EN | 1 | 15 | 4 | 3 | Budget report with categories |
| R03_sales.xlsx | EN | 3 | 213 | 25 | 1 | Sales report with summary |
| R05_financial.xlsx | EN | 2 | 22 | 5 | 2 | Income statement + balance sheet |
| R06_product-catalog.xlsx | EN | 1 | 502 | 8 | 1 | Product catalog (500 items) |
| R07_quarterly.xlsx | EN | 3 | 60 | 10 | 3 | Quarterly financial (3 sheets) |
| R09_schedule.xlsx | EN | 1 | 9 | 6 | 3 | Weekly schedule with merged cells |
| R10_time-tracking.xlsx | EN | 1 | 152 | 30 | 5 | Time tracking (150 employees) |

## Results

Latest: [v0.1.0](v0.1.0.md) (2026-04-18)

|              | read4ai-excel v0.1.0 | pandas 3.0 | SheetJS 0.20 | calamine 0.6 |
|--------------|----------------------|------------|--------------|--------------|
| GPT-5.4 mini | 🏆 **87.5%**         | 82.0%      | 83.6%        | 82.8%        |

Evaluated on 25+ fixtures / 128+ questions with GPT-5.4 mini. See [v0.1.0.md](v0.1.0.md) for per-fixture Q&A and expected/actual answers.

## Submit your golden set

Have a tricky Excel file? Submit your file, a question, and the expected answer. Submitted fixtures and Q&A will be **fully published** in the benchmark.

Contact: [GitHub](https://github.com/Hyune-c) · [LinkedIn](https://www.linkedin.com/in/b30b971a0/)
