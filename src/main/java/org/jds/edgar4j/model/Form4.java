package org.jds.edgar4j.model;

/**
 * Backwards compatibility wrapper for Form 4
 * This class is deprecated - use InsiderForm with formType="4" instead
 *
 * Form 4: Statement of Changes in Beneficial Ownership
 * Filed by company insiders when they buy or sell company stock
 *
 * @deprecated Use {@link InsiderForm} with formType="4" instead
 * @author J. Daniel Sobrado
 * @version 3.0
 * @since 2025-11-05
 */
@Deprecated
public class Form4 extends InsiderForm {

    public Form4() {
        super();
        setFormType("4");
    }

    /**
     * Create Form4 from InsiderForm
     * @param insiderForm the insider form to convert
     * @return Form4 instance
     */
    public static Form4 from(InsiderForm insiderForm) {
        if (!"4".equals(insiderForm.getFormType())) {
            throw new IllegalArgumentException("Cannot create Form4 from formType=" + insiderForm.getFormType());
        }

        Form4 form4 = new Form4();
        // Copy all fields from parent
        form4.setId(insiderForm.getId());
        form4.setAccessionNumber(insiderForm.getAccessionNumber());
        form4.setFilingDate(insiderForm.getFilingDate());
        form4.setPeriodOfReport(insiderForm.getPeriodOfReport());
        form4.setIssuerCik(insiderForm.getIssuerCik());
        form4.setIssuerName(insiderForm.getIssuerName());
        form4.setTradingSymbol(insiderForm.getTradingSymbol());
        form4.setReportingOwners(insiderForm.getReportingOwners());
        form4.setNonDerivativeTransactions(insiderForm.getNonDerivativeTransactions());
        form4.setDerivativeTransactions(insiderForm.getDerivativeTransactions());
        form4.setFootnotes(insiderForm.getFootnotes());
        form4.setRemarks(insiderForm.getRemarks());
        form4.setSignature(insiderForm.getSignature());
        form4.setSignatureDate(insiderForm.getSignatureDate());
        form4.setAmendment(insiderForm.isAmendment());
        form4.setOriginalFilingDate(insiderForm.getOriginalFilingDate());
        form4.setNotSubjectToSection16(insiderForm.isNotSubjectToSection16());
        form4.setFormFiledByMultiplePersons(insiderForm.isFormFiledByMultiplePersons());
        form4.setDocumentUrl(insiderForm.getDocumentUrl());

        return form4;
    }
}
