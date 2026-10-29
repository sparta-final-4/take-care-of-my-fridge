package org.example.takecareofmyfridgebe.infra.elasticsearch.config;

import org.springframework.core.io.Resource;

public interface EsIndexProperties {

    String alias();
    Resource indexSettings();
    Resource synonyms();
    Resource extraSynonyms();
    int batchSize();

}
