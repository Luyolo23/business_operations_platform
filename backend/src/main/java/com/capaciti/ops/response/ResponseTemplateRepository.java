package com.capaciti.ops.response;

import com.capaciti.ops.ticket.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResponseTemplateRepository extends JpaRepository<ResponseTemplate, Long> {
    Optional<ResponseTemplate> findByCategoryAndTone(Category category, Tone tone);
}
