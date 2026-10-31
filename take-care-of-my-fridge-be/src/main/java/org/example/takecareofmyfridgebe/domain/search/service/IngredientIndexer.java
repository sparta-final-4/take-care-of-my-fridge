package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.infra.elasticsearch.config.EsIndexProperties;
import org.example.takecareofmyfridgebe.infra.elasticsearch.index.EsDocumentReader;
import org.example.takecareofmyfridgebe.infra.elasticsearch.index.EsIndexer;
import org.example.takecareofmyfridgebe.infra.elasticsearch.index.SynonymRuleBuilder;
import org.springframework.stereotype.Component;

@Component
public class IngredientIndexer extends EsIndexer {

    private final IngredientDocumentReader documentReader;
    private final RecipeSearchProperties properties;

    public IngredientIndexer(
            ElasticsearchClient esClient,
            SynonymRuleBuilder synonymRuleBuilder,
            IngredientDocumentReader documentReader,
            RecipeSearchProperties properties
    ) {
        super(esClient, synonymRuleBuilder);
        this.documentReader = documentReader;
        this.properties = properties;
    }

    @Override
    protected EsIndexProperties indexProperties() {
        return properties.indexProperties().ingredient();
    }

    @Override
    protected EsDocumentReader documentReader() {
        return documentReader;
    }
}
