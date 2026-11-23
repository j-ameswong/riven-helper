package com.riven.helper.backend.manifest;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.riven.helper.backend.warframemarket.AttributeDTO;
import com.riven.helper.backend.warframemarket.I18nDTO;
import com.riven.helper.backend.warframemarket.MapperConfiguration;

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
