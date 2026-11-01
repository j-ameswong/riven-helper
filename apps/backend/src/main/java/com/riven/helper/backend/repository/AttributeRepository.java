package com.riven.helper.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.riven.helper.backend.domain.Attribute;

public interface AttributeRepository extends JpaRepository<Attribute, Long> {

	Optional<Attribute> findByApiAttributeId(String apiAttributeId);

	Optional<Attribute> findBySlug(String slug);

}
