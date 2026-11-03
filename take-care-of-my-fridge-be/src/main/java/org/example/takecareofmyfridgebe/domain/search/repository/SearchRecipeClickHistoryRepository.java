package org.example.takecareofmyfridgebe.domain.search.repository;

import org.example.takecareofmyfridgebe.domain.search.entity.SearchRecipeClickHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchRecipeClickHistoryRepository extends JpaRepository<SearchRecipeClickHistory, Long> {
}
