# Phase 3: Dividend Event Text Extraction

## Objective

Parse SEC filing text (8-K Item 8.01, Exhibit 99.1 press releases, and 10-K equity notes) to extract dividend declarations, suspensions, policy changes, and special dividends — events that are frequently described in narrative text rather than in a single neat XBRL tag.

## Current Implementation Status

Implemented local coverage:

- Declaration extraction from representative Item 8.01 text, including per-share amount, declaration date, record date, and payable date.
- Special dividend extraction from representative current-report text.
- Suspension-language extraction without creating a false declaration event.
- Policy-language extraction from annual filing text.
- False-positive guard for general dividend-yield discussion.
- Inline XBRL/HTML cleaning without corrupting visible text.

Remaining operational validation:

- Live recent AAPL 8-K extraction and comparison against known dividend history.

## Why Text Extraction Is Necessary

XBRL gives you the **numbers** (DPS declared, dividends paid), but the **events** live in filing text:
- Declaration date, record date, payable date
- Dividend type (regular, special, one-time, interim)
- Suspension or elimination announcements
- Board discretion / policy language
- Increases or decreases in the dividend amount
- Covenant-driven restrictions on dividends

## Data Model

```
┌──────────────────────────────────────────────────────────────┐
│ dividend_events                                               │
├──────────────────────────────────────────────────────────────┤
│ id                  BIGINT        PK (auto)                  │
│ cik                 VARCHAR(10)   FK → dim_dividend_company  │
│ accession           VARCHAR(25)   -- source filing           │
│ form                VARCHAR(10)   -- "8-K", "10-K", etc.     │
│ event_type          VARCHAR(30)   -- DECLARATION, SUSPENSION,│
│                                      INCREASE, DECREASE,     │
│                                      SPECIAL, POLICY_CHANGE, │
│                                      REINSTATEMENT           │
│ amount_per_share    DECIMAL(10,4) -- NULL if not parseable   │
│ currency            VARCHAR(3)    -- "USD" default           │
│ dividend_type       VARCHAR(20)   -- REGULAR, SPECIAL,       │
│                                      QUARTERLY, MONTHLY,     │
│                                      ANNUAL, INTERIM         │
│ declaration_date    DATE                                      │
│ record_date         DATE                                      │
│ payable_date        DATE                                      │
│ text_snippet        TEXT          -- source text (500 chars)  │
│ policy_language     TEXT          -- extracted policy clause  │
│ confidence          VARCHAR(10)   -- HIGH, MEDIUM, LOW       │
│ extraction_method   VARCHAR(20)   -- REGEX, NER, MANUAL      │
│ source_section      VARCHAR(50)   -- "ITEM_8.01", "EX_99.1" │
│ filed_date          DATE                                      │
│ created_at          TIMESTAMP                                 │
│ updated_at          TIMESTAMP                                 │
│                                                               │
│ INDEX: (cik, event_type, declaration_date)                   │
│ INDEX: (cik, filed_date DESC)                                │
└──────────────────────────────────────────────────────────────┘
```

## Filing Text Sources (Priority Order)

### 1. 8-K Item 8.01 (Other Events) + Exhibit 99.1

The most common and structured location for dividend declarations. Item 8.01 is for optional disclosure of important events, and dividend declarations are commonly disclosed here with an attached press release.

**Access pattern:**
1. Use EFTS search (existing `SecApiClient.fetchEftsSearch()`) to find 8-K filings
2. Fetch the filing index page (`-index.html`) from EDGAR archives
3. Parse the index to find Item 8.01 section and Exhibit 99.1 document
4. Extract text from the relevant document

### 2. 10-K / 10-Q Equity Notes

Dividend schedules, policy descriptions, and restriction language in the "Stockholders' Equity" or "Dividends" note.

### 3. 10-K Risk Factors and MD&A

Forward-looking dividend policy language, covenant restrictions, and sustainability statements.

## Extraction Patterns

### 3.1 Regex Patterns

```java
public class DividendTextPatterns {

    // ── Declaration ─────────────────────────────────────
    public static final Pattern DECLARATION = Pattern.compile(
        "(?i)\\b(board of directors|board)\\b.*\\b(declared|approved|authorized)\\b.*" +
        "\\b(cash dividend|dividend|distribution)\\b", Pattern.DOTALL);

    // ── Dividend type ───────────────────────────────────
    public static final Pattern DIVIDEND_TYPE = Pattern.compile(
        "(?i)\\b(quarterly|monthly|semi-annual|annual|interim|final|special|one-time|" +
        "extraordinary|supplemental)\\b.*\\bdividend\\b");

    // ── Per-share amount ────────────────────────────────
    public static final Pattern AMOUNT_PER_SHARE = Pattern.compile(
        "(?i)\\$\\s?(?<amount>\\d+(\\.\\d+)?)\\s*(per\\s+(common\\s+)?share|a\\s+share)\\b");

    // ── Record date ─────────────────────────────────────
    public static final Pattern RECORD_DATE = Pattern.compile(
        "(?i)\\b(record\\s+date|holders\\s+of\\s+record|stockholders\\s+of\\s+record)\\b" +
        ".*?\\b(as\\s+of|at\\s+the\\s+close\\s+of\\s+business\\s+on|on)\\b\\s+" +
        "(?<date>[A-Za-z]+\\s+\\d{1,2},?\\s+\\d{4})");

    // ── Payable date ────────────────────────────────────
    public static final Pattern PAYABLE_DATE = Pattern.compile(
        "(?i)\\b(payable|to\\s+be\\s+paid|payment\\s+date)\\b\\s+" +
        "(on|by|of)\\s+(?<date>[A-Za-z]+\\s+\\d{1,2},?\\s+\\d{4})");

    // ── Suspension / elimination ────────────────────────
    public static final Pattern SUSPENSION = Pattern.compile(
        "(?i)\\b(suspend(ed|ing)?|omit(ted|ting)?|eliminate(d|ing)?|" +
        "discontinue(d|ing)?)\\b.*\\b(dividend|dividends|distribution)\\b");

    // ── Increase / decrease ─────────────────────────────
    public static final Pattern INCREASE = Pattern.compile(
        "(?i)\\b(increase[ds]?|raise[ds]?|higher|grew|growing)\\b.*" +
        "\\b(dividend|dividends)\\b");

    public static final Pattern DECREASE = Pattern.compile(
        "(?i)\\b(reduce[ds]?|decrease[ds]?|cut[s]?|lower(ed|ing)?)\\b.*" +
        "\\b(dividend|dividends)\\b");

    // ── Policy / discretion language ────────────────────
    public static final Pattern POLICY_LANGUAGE = Pattern.compile(
        "(?i)\\b(at\\s+the\\s+discretion\\s+of\\s+the\\s+board|" +
        "subject\\s+to\\s+(the\\s+)?board|" +
        "may\\s+(amend|revoke|suspend|modify|change)|" +
        "depends\\s+on\\s+(earnings|cash\\s+flow|debt)|" +
        "no\\s+assurance|" +
        "future\\s+dividends\\s+will\\s+(be|depend)|" +
        "covenant|restricted\\s+payments)\\b");
}
```

### 3.2 DividendEventExtractor Service

```java
@Service
@RequiredArgsConstructor
public class DividendEventExtractor {

    public List<DividendEvent> extract(String text, String accession,
                                        String form, String cik, LocalDate filedDate) {
        List<DividendEvent> events = new ArrayList<>();

        // 1. Check for declaration
        if (DividendTextPatterns.DECLARATION.matcher(text).find()) {
            DividendEvent event = new DividendEvent();
            event.setCik(cik);
            event.setAccession(accession);
            event.setForm(form);
            event.setFiledDate(filedDate);
            event.setEventType(EventType.DECLARATION);

            // Extract amount, type, dates
            Matcher amountMatcher = DividendTextPatterns.AMOUNT_PER_SHARE.matcher(text);
            if (amountMatcher.find()) {
                event.setAmountPerShare(new BigDecimal(amountMatcher.group("amount")));
                event.setConfidence("HIGH");
            } else {
                event.setConfidence("MEDIUM");
            }
            // ... extract type, record date, payable date similarly
            events.add(event);
        }

        // 2. Check for suspension, increase, decrease
        if (DividendTextPatterns.SUSPENSION.matcher(text).find()) {
            events.add(buildEvent(cik, accession, EventType.SUSPENSION, "HIGH"));
        }

        // 3. Extract policy language and attach to events
        Matcher policyMatcher = DividendTextPatterns.POLICY_LANGUAGE.matcher(text);
        while (policyMatcher.find()) {
            String policySnippet = extractSnippet(text, policyMatcher.start());
            if (!events.isEmpty()) {
                events.get(events.size() - 1).setPolicyLanguage(policySnippet);
            }
        }

        return events;
    }
}
```

### 3.3 HTML/Text Cleaning

```java
public class FilingTextCleaner {
    public static String clean(String rawHtml) {
        Document doc = Jsoup.parse(rawHtml);
        doc.select("script, style, [class*=hidden]").remove();
        String text = doc.text();
        text = text.replaceAll("\\s+", " ").trim();
        text = text.replace("\u00A0", " ")
                   .replace("\u2019", "'")
                   .replace("\u201C", "\"")
                   .replace("\u201D", "\"");
        return text;
    }
}
```

## Event Types and Confidence

| Event Type | Source | Detection | Confidence |
|---|---|---|---|
| `DECLARATION` | 8-K Item 8.01 / Ex 99.1 | Regex: "declared...dividend" + $ amount | HIGH |
| `SPECIAL` | 8-K / 10-K | Regex: "special dividend" + $ amount | HIGH |
| `INCREASE` | 8-K / 10-K | Regex: "increase...dividend" | MEDIUM |
| `DECREASE` | 8-K / 10-K | Regex: "reduce/cut...dividend" | MEDIUM |
| `SUSPENSION` | 8-K / 10-K | Regex: "suspend/eliminate...dividend" | HIGH |
| `REINSTATEMENT` | 8-K | Regex: "reinstate/resume...dividend" | MEDIUM |
| `POLICY_CHANGE` | 10-K risk factors | Regex: "may amend/suspend" | LOW-MEDIUM |

## Validation Checklist

- [ ] Apple (AAPL) dividend declarations extracted from recent 8-K filings
- [ ] Per-share amounts match known dividend history
- [x] Record date and payable date correctly parsed
- [x] Special dividends correctly flagged
- [x] Suspension events detected
- [x] Policy language extracted from 10-K risk factors
- [x] No false positives from unrelated uses of "dividend"
- [x] HTML cleaning handles inline XBRL markup without corrupting text

## Estimated Effort: 5-6 days
