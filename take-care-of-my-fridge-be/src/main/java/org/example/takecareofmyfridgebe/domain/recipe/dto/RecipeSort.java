package org.example.takecareofmyfridgebe.domain.recipe.dto;

import org.springframework.data.domain.Sort;

public enum RecipeSort {
    LATEST(Sort.by(Sort.Direction.DESC, "id")),
    NAME_ASC(Sort.by(Sort.Order.asc("name"), Sort.Order.desc("id"))),
    DIFFICULTY_ASC(Sort.by(Sort.Order.asc("difficulty"), Sort.Order.desc("id")));

    private final Sort sort;

    RecipeSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }
}
