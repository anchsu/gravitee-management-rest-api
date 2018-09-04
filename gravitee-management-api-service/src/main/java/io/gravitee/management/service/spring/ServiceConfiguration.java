/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.management.service.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.event.impl.EventManagerImpl;
import io.gravitee.definition.jackson.datatype.GraviteeMapper;
import io.gravitee.management.fetcher.spring.FetcherConfigurationConfiguration;
import io.gravitee.management.service.jackson.filter.ApiPermissionFilter;
import io.gravitee.management.service.quality.ApiQualityMetricLoader;
import io.gravitee.node.notifier.spring.NotifierPluginConfiguration;
import io.gravitee.plugin.alert.spring.AlertPluginConfiguration;
import io.gravitee.plugin.fetcher.spring.FetcherPluginConfiguration;
import io.gravitee.plugin.policy.spring.PolicyPluginConfiguration;
import io.gravitee.plugin.resource.spring.ResourcePluginConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Collections;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@ComponentScan("io.gravitee.management.service")
@EnableTransactionManagement
@Import({PolicyPluginConfiguration.class, ResourcePluginConfiguration.class, FetcherPluginConfiguration.class,
		FetcherConfigurationConfiguration.class, NotifierPluginConfiguration.class, AlertPluginConfiguration.class})
public class ServiceConfiguration {

	@Bean
	public EventManager eventManager() {
		return new EventManagerImpl();
	}

	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper objectMapper = new GraviteeMapper();
		PropertyFilter apiMembershipTypeFilter = new ApiPermissionFilter();
		objectMapper.setFilterProvider(new SimpleFilterProvider(Collections.singletonMap("apiMembershipTypeFilter", apiMembershipTypeFilter)));
		return objectMapper;
	}
	@Bean
	public ApiQualityMetricLoader apiQualityMetricLoader() {
		return new ApiQualityMetricLoader();
	}
}
