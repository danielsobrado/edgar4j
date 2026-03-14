package org.jds.edgar4j.adapter.file;

import java.time.LocalDate;

import org.jds.edgar4j.model.Form5;
import org.jds.edgar4j.port.Form5DataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class Form5FileAdapter extends AbstractSimpleFilingFileAdapter<Form5> implements Form5DataPort {

    public Form5FileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "form5",
                Form5.class,
                FileFormat.JSONL,
                Form5::getId,
                Form5::setId));
        registerCommonFilingIndexes();
    }

    @Override
    protected String getAccessionNumber(Form5 value) {
        return value.getAccessionNumber();
    }

    @Override
    protected String getCik(Form5 value) {
        return value.getCik();
    }

    @Override
    protected String getTradingSymbol(Form5 value) {
        return value.getTradingSymbol();
    }

    @Override
    protected LocalDate getFiledDate(Form5 value) {
        return value.getFiledDate();
    }
}
