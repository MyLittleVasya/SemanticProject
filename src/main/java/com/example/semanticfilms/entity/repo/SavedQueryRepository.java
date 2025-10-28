package com.example.semanticfilms.entity.repo;

import com.example.semanticfilms.entity.SavedQuery;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedQueryRepository extends JpaRepository<SavedQuery, String> {

  Optional<SavedQuery> findByQueryUrl(String queryUrl);

}
