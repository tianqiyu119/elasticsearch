/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
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

package org.elasticsearch.discovery.single;

import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.InternalTestCluster;
import org.elasticsearch.test.MockHttpTransport;
import org.elasticsearch.test.NodeConfigurationSource;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Function;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@ESIntegTestCase.ClusterScope(
        scope = ESIntegTestCase.Scope.TEST,
        numDataNodes = 1,
        numClientNodes = 0,
        supportsDedicatedMasters = false,
        autoMinMasterNodes = false)
public class SingleNodeDiscoveryIT extends ESIntegTestCase {

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings
                .builder()
                .put(super.nodeSettings(nodeOrdinal))
                .put("discovery.type", "single-node")
                .put("transport.tcp.port", "0")
                .build();
    }

    public void testSingleNodesDoNotDiscoverEachOther() throws IOException, InterruptedException {
        final TransportService service = internalCluster().getInstance(TransportService.class);
        final int port = service.boundAddress().publishAddress().getPort();
        final NodeConfigurationSource configurationSource = new NodeConfigurationSource() {
            @Override
            public Settings nodeSettings(int nodeOrdinal) {
                return Settings
                        .builder()
                        .put("discovery.type", "single-node")
                        .put("transport.type", getTestTransportType())
                        /*
                         * We align the port ranges of the two as then with zen discovery these two
                         * nodes would find each other.
                         */
                        .put("transport.port", port + "-" + (port + 5 - 1))
                        .build();
            }

            @Override
            public Path nodeConfigPath(int nodeOrdinal) {
                return null;
            }
        };
        try (InternalTestCluster other =
                new InternalTestCluster(
                        randomLong(),
                        createTempDir(),
                        false,
                        false,
                        1,
                        1,
                        internalCluster().getClusterName(),
                        configurationSource,
                        0,
                        "other",
                        Arrays.asList(getTestTransportPlugin(), MockHttpTransport.TestPlugin.class),
                        Function.identity())) {
            other.beforeTest(random(), 0);
            final ClusterState first = internalCluster().getInstance(ClusterService.class).state();
            final ClusterState second = other.getInstance(ClusterService.class).state();
            assertThat(first.nodes().getSize(), equalTo(1));
            assertThat(second.nodes().getSize(), equalTo(1));
            assertThat(
                    first.nodes().getMasterNodeId(),
                    not(equalTo(second.nodes().getMasterNodeId())));
            assertThat(
                    first.metaData().clusterUUID(),
                    not(equalTo(second.metaData().clusterUUID())));
        }
    }

    public void testStatePersistence() throws Exception {
        createIndex("test");
        internalCluster().fullRestart();
        assertTrue(client().admin().indices().prepareExists("test").get().isExists());
    }

}
