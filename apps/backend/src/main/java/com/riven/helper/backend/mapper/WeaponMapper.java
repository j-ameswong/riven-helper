package com.riven.helper.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.riven.helper.backend.domain.RivenType;
import com.riven.helper.backend.domain.Weapon;
import com.riven.helper.backend.domain.WeaponGroup;
import com.riven.helper.backend.dto.I18nDTO;
import com.riven.helper.backend.dto.WeaponDTO;

@Mapper(config = MapperConfiguration.class)
public interface WeaponMapper {

	/** A new, unsaved weapon; {@code weaponId} is left null. */
	@Mapping(target = "weaponId", ignore = true)
	@Mapping(target = "apiWeaponId", source = "id")
	@Mapping(target = "weaponGroup", source = "group")
	@Mapping(target = "rivenType", source = "dto", qualifiedByName = "rivenType")
	@Mapping(target = "name", source = "dto", qualifiedByName = "englishName")
	Weapon toEntity(WeaponDTO dto);

	// The API calls archgun rivens rifle; in game they're their own type
	@Named("rivenType")
	default RivenType rivenType(WeaponDTO dto) {
		return dto.group() == WeaponGroup.ARCHGUN ? RivenType.ARCHGUN : dto.rivenType();
	}

	@Named("englishName")
	default String englishName(WeaponDTO dto) {
		return I18nDTO.englishName(dto.i18n(), dto.slug());
	}

}
