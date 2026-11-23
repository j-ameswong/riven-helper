package com.riven.helper.backend.manifest;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface AttributeRepository extends JpaRepository<Attribute, Long> {

	Optional<Attribute> findByApiAttributeId(String apiAttributeId);

	Optional<Attribute> findBySlug(String slug);

}
