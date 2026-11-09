package com.riven.helper.backend;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Runs the {@code @Scheduled} syncs. Tests turn it off with
 * {@code riven-helper.scheduling.enabled=false} so they don't call the real API.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "riven-helper.scheduling.enabled", matchIfMissing = true)
class SchedulingConfiguration {

}
