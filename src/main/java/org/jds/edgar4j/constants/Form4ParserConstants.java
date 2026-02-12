package org.jds.edgar4j.constants;

/**
 * SEC Form 4 XML parsing constants
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
public final class Form4ParserConstants {
    
    // XML Namespaces
    public static final String SEC_NAMESPACE = "http://www.sec.gov/edgar/document/thirteenf/informationtable";
    public static final String EDGAR_NAMESPACE = "http://www.sec.gov/edgar/common";
    
    // Root elements
    public static final String OWNERSHIP_DOCUMENT = "ownershipDocument";
    public static final String SCHEMA_VERSION = "schemaVersion";
    
    // Document info elements
    public static final String DOCUMENT_TYPE = "documentType";
    public static final String PERIOD_OF_REPORT = "periodOfReport";
    public static final String NOT_SUBJECT_TO_SECTION = "notSubjectToSection16";
    public static final String ISSUER = "issuer";
    public static final String REPORTING_OWNER = "reportingOwner";
    
    // Issuer elements
    public static final String ISSUER_CIK = "issuerCik";
    public static final String ISSUER_NAME = "issuerName";
    public static final String ISSUER_TRADING_SYMBOL = "issuerTradingSymbol";
    
    // Reporting owner elements
    public static final String REPORTING_OWNER_CIK = "reportingOwnerCik";
    public static final String REPORTING_OWNER_NAME = "reportingOwnerName";
    public static final String REPORTING_OWNER_ADDRESS = "reportingOwnerAddress";
    
    // Address elements
    public static final String ADDRESS_STREET1 = "rptOwnerStreet1";
    public static final String ADDRESS_STREET2 = "rptOwnerStreet2";
    public static final String ADDRESS_CITY = "rptOwnerCity";
    public static final String ADDRESS_STATE = "rptOwnerState";
    public static final String ADDRESS_ZIP = "rptOwnerZipCode";
    public static final String ADDRESS_STATE_DESCRIPTION = "rptOwnerStateDescription";
    
    // Relationship elements
    public static final String REPORTING_OWNER_RELATIONSHIP = "reportingOwnerRelationship";
    public static final String IS_DIRECTOR = "isDirector";
    public static final String IS_OFFICER = "isOfficer";
    public static final String IS_TEN_PERCENT_OWNER = "isTenPercentOwner";
    public static final String IS_OTHER = "isOther";
    public static final String OFFICER_TITLE = "officerTitle";
    public static final String OTHER_TEXT = "otherText";
    
    // Transaction elements
    public static final String NON_DERIVATIVE_TABLE = "nonDerivativeTable";
    public static final String DERIVATIVE_TABLE = "derivativeTable";
    public static final String NON_DERIVATIVE_TRANSACTION = "nonDerivativeTransaction";
    public static final String DERIVATIVE_TRANSACTION = "derivativeTransaction";
    
    // Security title
    public static final String SECURITY_TITLE = "securityTitle";
    public static final String SECURITY_TITLE_VALUE = "value";
    
    // Transaction date
    public static final String TRANSACTION_DATE = "transactionDate";
    public static final String TRANSACTION_DATE_VALUE = "value";
    
    // Transaction coding
    public static final String TRANSACTION_CODING = "transactionCoding";
    public static final String TRANSACTION_FORM_TYPE = "transactionFormType";
    public static final String TRANSACTION_CODE = "transactionCode";
    public static final String EQUITY_SWAP_INVOLVED = "equitySwapInvolved";
    
    // Transaction amounts
    public static final String TRANSACTION_AMOUNTS = "transactionAmounts";
    public static final String TRANSACTION_SHARES = "transactionShares";
    public static final String TRANSACTION_PRICE_PER_SHARE = "transactionPricePerShare";
    public static final String TRANSACTION_ACQUIRED_DISPOSED_CODE = "transactionAcquiredDisposedCode";
    
    // Post transaction amounts
    public static final String POST_TRANSACTION_AMOUNTS = "postTransactionAmounts";
    public static final String SHARES_OWNED_FOLLOWING_TRANSACTION = "sharesOwnedFollowingTransaction";
    
    // Ownership nature
    public static final String OWNERSHIP_NATURE = "ownershipNature";
    public static final String DIRECT_OR_INDIRECT_OWNERSHIP = "directOrIndirectOwnership";
    public static final String NATURE_OF_OWNERSHIP = "natureOfOwnership";
    
    // Signature
    public static final String SIGNATURE = "signature";
    public static final String SIGNATURE_DATE = "signatureDate";
    public static final String SIGNATURE_NAME = "signatureName";
    
    // Footnotes
    public static final String FOOTNOTES = "footnotes";
    public static final String FOOTNOTE = "footnote";
    public static final String FOOTNOTE_ID = "id";
    
    // Value attributes
    public static final String VALUE_ATTR = "value";
    public static final String FOOTNOTE_ATTR = "footnoteId";
    
    // Common values
    public static final String VALUE_TRUE = "1";
    public static final String VALUE_FALSE = "0";
    public static final String ACQUIRED_CODE = "A";
    public static final String DISPOSED_CODE = "D";
    public static final String DIRECT_OWNERSHIP = "D";
    public static final String INDIRECT_OWNERSHIP = "I";
    
    // Date format
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    
    private Form4ParserConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}