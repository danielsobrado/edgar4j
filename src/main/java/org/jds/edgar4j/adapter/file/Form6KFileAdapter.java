package org.jds.edgar4j.adapter.file;

import java.time.LocalDate;

import org.jds.edgar4j.model.Form6K;
import org.jds.edgar4j.port.Form6KDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class Form6KFileAdapter extends AbstractSimpleFilingFileAdapter<Form6K> implements Form6KDataPort {

    public Form6KFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "form6k",
                Form6K.class,
                FileFormat.JSONL,
                Form6K::getId,
                Form6K::setId));
        registerCommonFilingIndexes();
    }

    @Override
    protected String getAccessionNumber(Form6K value) {
        return value.getAccessionNumber();
    }

    @Override
    protected String getCik(Form6K value) {
        return value.getCik();
    }

    @Override
    protected String getTradingSymbol(Form6K value) {
        return value.getTradingSymbol();
    }

    @Override
    protected LocalDate getFiledDate(Form6K value) {
        return value.getFiledDate();
    }
}
