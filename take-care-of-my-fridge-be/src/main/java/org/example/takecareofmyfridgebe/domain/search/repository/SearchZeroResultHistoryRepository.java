package org.example.takecareofmyfridgebe.domain.search.repository;

import org.example.takecareofmyfridgebe.domain.search.entity.SearchZeroResultHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchZeroResultHistoryRepository extends JpaRepository<SearchZeroResultHistory, Long> {
}
