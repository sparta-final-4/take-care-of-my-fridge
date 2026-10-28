package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.example.takecareofmyfridgebe.domain.search.config.EsIndexProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.springframework.stereotype.Component;

@Component
public class RecipeIndexer extends EsIndexer {

    private final RecipeDocumentReader documentReader;
    private final RecipeSearchProperties properties;

    public RecipeIndexer(
            ElasticsearchClient esClient,
            SynonymRuleBuilder synonymRuleBuilder,
            RecipeDocumentReader documentReader,
            RecipeSearchProperties properties
    ) {
        super(esClient, synonymRuleBuilder);
        this.documentReader = documentReader;
        this.properties = properties;
    }

    @Override
    protected EsIndexProperties indexProperties() {
        return properties.indexProperties().recipe();
    }

    @Override
    protected EsDocumentReader documentReader() {
        return documentReader;
    }
}
