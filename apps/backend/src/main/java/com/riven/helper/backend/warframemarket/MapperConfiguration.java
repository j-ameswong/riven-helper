package com.riven.helper.backend.warframemarket;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Shared by every mapper, which convert warframe.market DTOs into domain entities
 * so that services only ever take entities whatever their source. MapStruct
 * generates each as a Spring bean, and a new entity field with no mapping fails
 * the build rather than staying null.
 */
@MapperConfig(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MapperConfiguration {

}
