/**
 * blackduck-artifactory-common
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.blackduck.artifactory.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.blackduck.artifactory.configuration.model.PropertyGroupReport;
import com.synopsys.integration.blackduck.artifactory.modules.analytics.service.AnalyticsService;
import com.synopsys.integration.builder.BuilderStatus;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;

public class ModuleManager {
    private final IntLogger logger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));
    private final List<Module> registeredModules = new ArrayList<>();
    private final List<Module> allModules = new ArrayList<>();

    private final AnalyticsService analyticsService;

    public ModuleManager(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    public void setModulesState(Map<String, List<String>> params) {
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                String moduleStateRaw = entry.getValue().get(0);
                boolean moduleState = BooleanUtils.toBoolean(moduleStateRaw);
                String moduleName = entry.getKey();
                List<ModuleConfig> moduleConfigs = getModuleConfigsByName(moduleName);

                if (moduleConfigs.isEmpty()) {
                    logger.warn(String.format("No registered modules with the name '%s' found. Hit the blackDuckTestConfig endpoint to see why.", moduleName));
                } else {
                    moduleConfigs.forEach(moduleConfig -> {
                        logger.warn(String.format("Setting %s's enabled state to %b", moduleConfig.getModuleName(), moduleState));
                        moduleConfig.setEnabled(moduleState);
                    });
                }
            }
        }
    }

    private void registerModule(Module module) {
        ModuleConfig moduleConfig = module.getModuleConfig();
        BuilderStatus builderStatus = new BuilderStatus();
        PropertyGroupReport propertyGroupReport = new PropertyGroupReport(moduleConfig.getModuleName(), builderStatus);
        moduleConfig.validate(propertyGroupReport);

        allModules.add(module);
        if (!propertyGroupReport.hasError()) {
            registeredModules.add(module);
            analyticsService.registerAnalyzable(module);
            logger.info(String.format("Successfully registered '%s'.", moduleConfig.getModuleName()));
        } else {
            moduleConfig.setEnabled(false);
            logger.warn(String.format("Can't register module '%s' due to an invalid configuration. See details in the Status Check.", moduleConfig.getModuleName()));
        }
    }

    public void registerModules(Module... modules) {
        for (Module module : modules) {
            registerModule(module);
        }
    }

    /**
     * @return a list of ModuleConfig from registered modules with valid configurations
     */
    public List<ModuleConfig> getModuleConfigs() {
        return registeredModules.stream()
                   .map(Module::getModuleConfig)
                   .collect(Collectors.toList());
    }

    /**
     * @return a list of all ModuleConfig regardless of validation status
     */
    public List<ModuleConfig> getAllModuleConfigs() {
        return allModules.stream()
                   .map(Module::getModuleConfig)
                   .collect(Collectors.toList());
    }

    public List<ModuleConfig> getModuleConfigsByName(String moduleName) {
        return getModuleConfigs().stream()
                   .filter(moduleConfig -> moduleConfig.getModuleName().equalsIgnoreCase(moduleName))
                   .collect(Collectors.toList());
    }

    public Optional<ModuleConfig> getFirstModuleConfigByName(String moduleName) {
        return getModuleConfigsByName(moduleName).stream().findAny();
    }

    public void setAllModulesEnabledState(boolean isModuleEnabled) {
        getAllModuleConfigs().forEach(moduleConfig -> moduleConfig.setEnabled(isModuleEnabled));
    }
}
