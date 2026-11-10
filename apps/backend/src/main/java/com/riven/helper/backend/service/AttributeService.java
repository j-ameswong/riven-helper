package com.riven.helper.backend.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.riven.helper.backend.domain.Attribute;
import com.riven.helper.backend.dto.AttributeDTO;
import com.riven.helper.backend.dto.I18nDTO;
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
	public void upsertAll(List<AttributeDTO> dtos) {
		Map<String, Attribute> existing = attributeRepository.findAll().stream()
				.collect(Collectors.toMap(Attribute::getApiAttributeId, Function.identity()));
		attributeRepository.saveAll(dtos.stream().map(dto -> {
			Attribute attribute = existing.getOrDefault(dto.id(), new Attribute());
			attribute.setApiAttributeId(dto.id());
			attribute.setSlug(dto.slug());
			attribute.setGameRef(dto.gameRef());
			attribute.setAttributeGroup(dto.group());
			attribute.setPrefix(dto.prefix());
			attribute.setSuffix(dto.suffix());
			attribute.setUnit(dto.unit());
			attribute.setName(I18nDTO.englishName(dto.i18n(), dto.slug()));
			attribute.setExclusiveTo(dto.exclusiveTo());
			attribute.setPositiveOnly(dto.positiveOnly());
			attribute.setNegativeOnly(dto.negativeOnly());
			attribute.setPositiveIsNegative(dto.positiveIsNegative());
			return attribute;
		}).toList());
	}

}
