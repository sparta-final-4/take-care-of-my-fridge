package org.example.takecareofmyfridgebe.domain.search.repository;

import org.example.takecareofmyfridgebe.domain.search.entity.SearchClickHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchClickHistoryRepository extends JpaRepository<SearchClickHistory, Long> {
}
