# Introduction to Edgar4J

The goal of this library is to provide a framework for collecting and organizing fillings from the SEC website.

The scope will initially be confined to stock ownership and insider transactions.

Based on: https://www.sec.gov/edgar/sec-api-documentation

## Submissions API

Each entity’s current filing history is available at the following URL:

https://data.sec.gov/submissions/CIK##########.json
Where the ########## is the entity’s 10-digit Central Index Key (CIK), including leading zeros.

Example for Microsoft: https://data.sec.gov/submissions/CIK0000789019.json

## Bulk

## Forms

### Ownership: 13G and 13F

https://www.sec.gov/dera/data/form-13f

13G form is used to report a party's ownership of stock which exceeds 5% of a company's total stock issue.

Form 13F is a quarterly report that is required to be filed by all institutional investment managers with at least $100 million in assets under management.

### Insider Transactions: 3, 4 and 5

SEC Form 4: Statement of Changes in Beneficial Ownership? SEC Form 4: Statement of Changes in Beneficial Ownership is a document that must be filed with the Securities and Exchange Commission (SEC) whenever there is a material change in the holdings of company insiders.

Form 3 is a document that a company insider or major shareholder must file with the SEC. The information provided on the form is meant to disclose the holdings of directors, officers, and beneficial owners of registered companies and becomes public record.

SEC Form 5: Annual Statement of Changes in Beneficial Ownership of Securities is a document that company insiders must file with the Securities and Exchange Commission (SEC) if they have conducted transactions in the company's securities during the year.