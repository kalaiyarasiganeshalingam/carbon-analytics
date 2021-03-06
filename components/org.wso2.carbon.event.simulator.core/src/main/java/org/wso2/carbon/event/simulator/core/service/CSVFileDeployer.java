/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.event.simulator.core.service;

import org.apache.commons.io.FilenameUtils;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.deployment.engine.Artifact;
import org.wso2.carbon.deployment.engine.ArtifactType;
import org.wso2.carbon.deployment.engine.Deployer;
import org.wso2.carbon.deployment.engine.exception.CarbonDeploymentException;
import org.wso2.carbon.event.simulator.core.exception.CSVFileDeploymentException;
import org.wso2.carbon.event.simulator.core.internal.generator.csv.util.FileStore;
import org.wso2.carbon.event.simulator.core.internal.util.EventSimulatorConstants;
import org.wso2.carbon.stream.processor.common.EventStreamService;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * CSVFileDeployer is responsible for all simulation related CSV deployment tasks
 *
 */
@Component(
        name = "csv-file-deployer",
        immediate = true,
        service = org.wso2.carbon.deployment.engine.Deployer.class
)
public class CSVFileDeployer implements Deployer {
    private static final Logger log = LoggerFactory.getLogger(CSVFileDeployer.class);
    private ArtifactType artifactType = new ArtifactType<>(EventSimulatorConstants.CSV_FILE_EXTENSION);
    private URL directoryLocation;

    private void deployCSVFile(File file) throws Exception {
        if (!file.getName().startsWith(".")) {
            if (FilenameUtils.isExtension(file.getName(), EventSimulatorConstants.CSV_FILE_EXTENSION)) {
                FileStore.getFileStore().addFile(file.getName());
            } else {
                throw new CSVFileDeploymentException("Error: File extension not supported for file name "
                        + file.getName() + ". Support only ." + EventSimulatorConstants.CSV_FILE_EXTENSION + " .");
            }
        }
    }

    @Activate
    protected void activate(BundleContext bundleContext) {
        // Nothing to do.
    }

    @Override
    public void init() {
        try {
            directoryLocation = new URL("file:" + EventSimulatorConstants.DIRECTORY_CSV_FILES);
            log.info("Simulation CSV deployer initiated.");
        } catch (MalformedURLException e) {
            log.error("Error while initializing simulation CSV directoryLocation"
                    + EventSimulatorConstants.DIRECTORY_CSV_FILES, e);
        }
    }

    @Override
    public Object deploy(Artifact artifact) throws CarbonDeploymentException {
        try {
            deployCSVFile(artifact.getFile());
        } catch (Exception e) {
            throw new CarbonDeploymentException(e.getMessage(), e);
        }
        return artifact.getFile().getName();
    }

    @Override
    public void undeploy(Object key) throws CarbonDeploymentException {
        try {
            String fileName = (String) key;
            if (FileStore.getFileStore().checkExists(fileName)) {
                FileStore.getFileStore().deleteFile(fileName);
            }
        } catch (Exception e) {
            throw new CarbonDeploymentException(e.getMessage(), e);
        }
    }

    @Override
    public Object update(Artifact artifact) throws CarbonDeploymentException {
        // Nothing to do.
        return artifact.getName();
    }

    @Override
    public URL getLocation() {
        return directoryLocation;
    }

    @Override
    public ArtifactType getArtifactType() {
        return artifactType;
    }

    @Reference(
            name = "event.stream.service",
            service = EventStreamService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetEventStreamService"
    )
    protected void setEventStreamService(EventStreamService eventStreamService) {
        if (log.isDebugEnabled()) {
            log.info("@Reference(bind) EventStreamService");
        }

    }

    protected void unsetEventStreamService(EventStreamService eventStreamService) {
        if (log.isDebugEnabled()) {
            log.info("@Reference(unbind) EventStreamService");
        }

    }
}
