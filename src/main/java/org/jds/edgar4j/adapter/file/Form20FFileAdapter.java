package org.jds.edgar4j.adapter.file;

import java.time.LocalDate;

import org.jds.edgar4j.model.Form20F;
import org.jds.edgar4j.port.Form20FDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class Form20FFileAdapter extends AbstractSimpleFilingFileAdapter<Form20F> implements Form20FDataPort {

    public Form20FFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "form20f",
                Form20F.class,
                FileFormat.JSONL,
                Form20F::getId,
                Form20F::setId));
        registerCommonFilingIndexes();
    }

    @Override
    protected String getAccessionNumber(Form20F value) {
        return value.getAccessionNumber();
    }

    @Override
    protected String getCik(Form20F value) {
        return value.getCik();
    }

    @Override
    protected String getTradingSymbol(Form20F value) {
        return value.getTradingSymbol();
    }

    @Override
    protected LocalDate getFiledDate(Form20F value) {
        return value.getFiledDate();
    }
}
