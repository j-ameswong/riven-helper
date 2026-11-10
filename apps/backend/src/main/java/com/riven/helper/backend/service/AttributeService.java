package com.riven.helper.backend.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.riven.helper.backend.domain.Attribute;
import com.riven.helper.backend.repository.AttributeRepository;

/** Owns every write to the attributes table. */
@Service
public class AttributeService {

	private final AttributeRepository attributeRepository;

	AttributeService(AttributeRepository attributeRepository) {
		this.attributeRepository = attributeRepository;
	}

	/** Inserts new attributes and updates existing ones in place, matched on the API id. */
	@Transactional
	public void upsertAll(List<Attribute> attributes) {
		Map<String, Attribute> existing = attributeRepository.findAll().stream()
				.collect(Collectors.toMap(Attribute::getApiAttributeId, Function.identity()));
		attributes.forEach(attribute -> {
			Attribute current = existing.get(attribute.getApiAttributeId());
			// An id makes saveAll merge onto the existing row instead of inserting
			attribute.setAttributeId(current == null ? null : current.getAttributeId());
		});
		attributeRepository.saveAll(attributes);
	}

}
