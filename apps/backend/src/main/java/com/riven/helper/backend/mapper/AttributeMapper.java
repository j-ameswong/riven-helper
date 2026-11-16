package com.riven.helper.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.riven.helper.backend.domain.Attribute;
import com.riven.helper.backend.dto.AttributeDTO;
import com.riven.helper.backend.dto.I18nDTO;

@Mapper(config = MapperConfiguration.class)
public interface AttributeMapper {

	/** A new, unsaved attribute; {@code attributeId} is left null. */
	@Mapping(target = "attributeId", ignore = true)
	@Mapping(target = "apiAttributeId", source = "id")
	@Mapping(target = "attributeGroup", source = "group")
	@Mapping(target = "name", source = "dto", qualifiedByName = "englishName")
	Attribute toEntity(AttributeDTO dto);

	@Named("englishName")
	default String englishName(AttributeDTO dto) {
		return I18nDTO.englishName(dto.i18n(), dto.slug());
	}

}
