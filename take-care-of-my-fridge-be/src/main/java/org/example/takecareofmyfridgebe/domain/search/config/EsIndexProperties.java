package org.example.takecareofmyfridgebe.domain.search.config;

import org.springframework.core.io.Resource;

public interface EsIndexProperties {

    String alias();
    Resource indexSettings();
    Resource synonyms();
    Resource extraSynonyms();
    int batchSize();

}
