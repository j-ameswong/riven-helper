package com.riven.helper.backend.mapper;

import org.springframework.stereotype.Component;

import com.riven.helper.backend.domain.Attribute;
import com.riven.helper.backend.dto.AttributeDTO;
import com.riven.helper.backend.dto.I18nDTO;

@Component
public class AttributeMapper {

	/** A new, unsaved attribute; {@code attributeId} is left null. */
	public Attribute toEntity(AttributeDTO dto) {
		Attribute attribute = new Attribute();
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
	}

}
