package org.jds.edgar4j.adapter.file;

import org.jds.edgar4j.model.Company;
import org.jds.edgar4j.port.CompanyDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class CompanyFileAdapter extends AbstractFileDataPort<Company> implements CompanyDataPort {

    public CompanyFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "companies",
                Company.class,
                FileFormat.JSON,
                Company::getId,
                Company::setId));
    }
}
