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
package io.gravitee.management.service.alert;

import io.gravitee.alert.api.service.AlertTriggerService;
import io.gravitee.alert.api.trigger.Trigger;
import io.gravitee.common.component.Lifecycle;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.definition.model.Service;
import io.gravitee.definition.model.services.Services;
import io.gravitee.definition.model.services.healthcheck.HealthCheckService;
import io.gravitee.management.model.api.ApiEntity;
import io.gravitee.management.service.ApiService;
import io.gravitee.management.service.event.ApiEvent;
import io.gravitee.notifier.api.Notification;
import io.gravitee.plugin.alert.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AlertTriggerServiceImpl implements EventListener<ApiEvent, ApiEntity>, AlertTriggerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertTriggerServiceImpl.class);
    private static final String HC_CONDITION_FORMAT = "$[?(@.event.api == \"%s\" && @.type == \"HC\")]";

    private Set<String> hcAPIs = new HashSet<>();

    @Autowired
    private AlertService alertService;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private ApiService apiService;
    @Value("${alerts.notifier.email.host}")
    private String host;
    @Value("${alerts.notifier.email.port}")
    private String port;
    @Value("${alerts.notifier.email.username}")
    private String username;
    @Value("${alerts.notifier.email.password}")
    private String password;
    @Value("${alerts.notifier.email.from}")
    private String defaultFrom;
    @Value("${alerts.notifier.email.subject:[Gravitee.io] %s}")
    private String subject;

    @PostConstruct
    public void init() {
        eventManager.subscribeForEvents(this, ApiEvent.class);
    }

    @Override
    public void onEvent(final Event<ApiEvent, ApiEntity> event) {
        triggerAPIHC(event.content());
    }

    @Override
    public void triggerAll() {
        hcAPIs.clear();
        triggerAllAPIsHC();
    }

    private void triggerAllAPIsHC() {
        final Set<ApiEntity> apis = apiService.findAll();
        for (final ApiEntity api : apis) {
            triggerAPIHC(api);
        }
    }


    private void triggerAPIHC(final ApiEntity api) {
        final Services services = api.getServices();
        if (Lifecycle.State.STARTED.equals(api.getState()) && services != null && services.getAll() != null) {
            final Optional<Service> optionalHCService = services.getAll().stream()
                    .filter(service -> service instanceof HealthCheckService)
                    .filter(Service::isEnabled)
                    .findAny();

            if (optionalHCService.isPresent() && !hcAPIs.contains(api.getId())) {
                final String apiOwnerEmail = api.getPrimaryOwner().getEmail();

                if (apiOwnerEmail == null) {
                    LOGGER.warn("Alert cannot be sent cause the API owner {} has no configured email",
                            api.getPrimaryOwner().getDisplayName());
                } else {
                    final Trigger trigger = new Trigger();
                    trigger.setId(getHCId(api));
                    trigger.setCondition(format(HC_CONDITION_FORMAT, api.getId()));

                    final Notification notification = new Notification();
                    notification.setType("email");

                    final String jsonConfiguration = format("{" +
                            "\"from\": \"%s\"," +
                            "\"to\": \"%s\"," +
                            "\"subject\": \"%s\"," +
                            "\"templateName\": \"%s\"," +
                            "\"host\": \"%s\"," +
                            "\"port\": \"%s\"," +
                            "\"username\": \"%s\"," +
                            "\"password\": \"%s\"" +
                        "}",
                        defaultFrom,
                        apiOwnerEmail,
                        format(subject, "HealthCheck status changed for API ${event.api} to ${event.new_status}"),
                        "alert_HC.html",
                        host,
                        port,
                        username,
                        password);
                    notification.setJsonConfiguration(jsonConfiguration);
                    trigger.setNotifications(singletonList(notification));

                    alertService.send(trigger);
                    hcAPIs.add(api.getId());
                    return;
                }
            }
        }
        cancelAPIHC(api);
    }

    private void cancelAPIHC(ApiEntity api) {
        if (hcAPIs.contains(api.getId())) {
            LOGGER.info("Sending trigger cancel message...");
            final Trigger trigger = new Trigger();
            trigger.setId(getHCId(api));
            trigger.setCancel(true);
            alertService.send(trigger);
            hcAPIs.remove(api.getId());
            LOGGER.info("Message trigger cancel successfully sent!");
        }
    }

    private String getHCId(ApiEntity api) {
        return "HC-" + api.getId();
    }
}