package org.jds.edgar4j.adapter.file;

import java.time.LocalDate;

import org.jds.edgar4j.model.Form8K;
import org.jds.edgar4j.port.Form8KDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class Form8KFileAdapter extends AbstractSimpleFilingFileAdapter<Form8K> implements Form8KDataPort {

    public Form8KFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "form8k",
                Form8K.class,
                FileFormat.JSONL,
                Form8K::getId,
                Form8K::setId));
    }

    @Override
    protected String getAccessionNumber(Form8K value) {
        return value.getAccessionNumber();
    }

    @Override
    protected String getCik(Form8K value) {
        return value.getCik();
    }

    @Override
    protected String getTradingSymbol(Form8K value) {
        return value.getTradingSymbol();
    }

    @Override
    protected LocalDate getFiledDate(Form8K value) {
        return value.getFiledDate();
    }
}
