package org.jds.edgar4j.adapter.file;

import java.time.LocalDate;

import org.jds.edgar4j.model.Form3;
import org.jds.edgar4j.port.Form3DataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class Form3FileAdapter extends AbstractSimpleFilingFileAdapter<Form3> implements Form3DataPort {

    public Form3FileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "form3",
                Form3.class,
                FileFormat.JSONL,
                Form3::getId,
                Form3::setId));
    }

    @Override
    protected String getAccessionNumber(Form3 value) {
        return value.getAccessionNumber();
    }

    @Override
    protected String getCik(Form3 value) {
        return value.getCik();
    }

    @Override
    protected String getTradingSymbol(Form3 value) {
        return value.getTradingSymbol();
    }

    @Override
    protected LocalDate getFiledDate(Form3 value) {
        return value.getFiledDate();
    }
}
