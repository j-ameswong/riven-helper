package com.riven.helper.backend.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Shared by every mapper: MapStruct generates each as a Spring bean, and a new
 * entity field with no mapping fails the build rather than staying null.
 */
@MapperConfig(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
interface MapperConfiguration {

}
