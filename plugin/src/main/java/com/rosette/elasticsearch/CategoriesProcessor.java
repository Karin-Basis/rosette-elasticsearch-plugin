/*
* Copyright 2017 Basis Technology Corp.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.rosette.elasticsearch;

import com.basistech.rosette.api.HttpRosetteAPI;
import com.basistech.rosette.api.HttpRosetteAPIException;
import com.basistech.rosette.apimodel.CategoriesOptions;
import com.basistech.rosette.apimodel.CategoriesResponse;
import com.basistech.rosette.apimodel.DocumentRequest;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.ingest.ConfigurationUtils;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;

public class CategoriesProcessor extends RosetteAbstractProcessor {

    public static final String TYPE = "ros_categories";

    private static final Logger LOGGER = ESLoggerFactory.getLogger(CategoriesProcessor.class.getName());

    CategoriesProcessor(RosetteApiWrapper rosAPI, String tag, String inputField, String targetField) {
        super(rosAPI, tag, TYPE, inputField, targetField);
    }

    @Override
    public void processDocument(String inputText, IngestDocument ingestDocument) throws Exception {
        // call /categories endpoint and set the top result in the field
        DocumentRequest<CategoriesOptions> request = new DocumentRequest.Builder<CategoriesOptions>().content(inputText).build();
        CategoriesResponse response;
        try {
            // RosApi client binding's Jackson needs elevated privilege
            response = AccessController.doPrivileged((PrivilegedAction<CategoriesResponse>) () ->
                    rosAPI.getHttpRosetteAPI().perform(HttpRosetteAPI.CATEGORIES_SERVICE_PATH, request, CategoriesResponse.class)
            );
        } catch (HttpRosetteAPIException ex) {
            LOGGER.error(ex.getErrorResponse().getMessage());
            throw new ElasticsearchException(ex.getErrorResponse().getMessage(), ex);
        }

        if (response.getCategories() != null
                && !response.getCategories().isEmpty()
                && response.getCategories().get(0) != null
                && !Strings.isNullOrEmpty(response.getCategories().get(0).getLabel())) {
            ingestDocument.setFieldValue(targetField, response.getCategories().get(0).getLabel());
        } else {
            throw new ElasticsearchException(TYPE + " ingest processor failed to categorize document.");
        }
    }

    public static final class Factory implements Processor.Factory {
        private RosetteApiWrapper rosAPI;

        Factory(RosetteApiWrapper rosAPI) {
            this.rosAPI = rosAPI;
        }

        @Override
        public Processor create(Map<String, Processor.Factory> registry, String processorTag, Map<String, Object> config) throws Exception {
            String inputField = ConfigurationUtils.readStringProperty(TYPE, processorTag, config, "field");
            String targetField = ConfigurationUtils.readStringProperty(TYPE, processorTag, config, Parameters.TARGET_FIELD.name, Parameters.TARGET_FIELD.defaultValue);
            return new CategoriesProcessor(rosAPI, processorTag, inputField, targetField);
        }
    }

    enum Parameters {
        TARGET_FIELD("target_field", "ros_category");

        String name;
        String defaultValue;

        Parameters(String name, String defaultValue) {
            this.name = name;
            this.defaultValue = defaultValue;
        }
    }
}
