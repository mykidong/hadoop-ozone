/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.hdds.scm.container.placement.algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.hadoop.hdds.conf.ConfigurationSource;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.hdds.protocol.DatanodeDetails;
import org.apache.hadoop.hdds.protocol.MockDatanodeDetails;
import org.apache.hadoop.hdds.protocol.proto.HddsProtos.NodeState;
import org.apache.hadoop.hdds.scm.container.placement.metrics.SCMNodeMetric;
import org.apache.hadoop.hdds.scm.exceptions.SCMException;
import org.apache.hadoop.hdds.scm.net.NetConstants;
import org.apache.hadoop.hdds.scm.net.NetworkTopology;
import org.apache.hadoop.hdds.scm.net.NetworkTopologyImpl;
import org.apache.hadoop.hdds.scm.net.NodeSchema;
import org.apache.hadoop.hdds.scm.net.NodeSchemaManager;
import org.apache.hadoop.hdds.scm.node.NodeManager;

import org.apache.commons.lang.StringUtils;
import static org.apache.hadoop.hdds.scm.net.NetConstants.LEAF_SCHEMA;
import static org.apache.hadoop.hdds.scm.net.NetConstants.RACK_SCHEMA;
import static org.apache.hadoop.hdds.scm.net.NetConstants.ROOT_SCHEMA;
import org.hamcrest.MatcherAssert;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.mockito.Matchers.anyObject;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

/**
 * Test for the scm container rack aware placement.
 */
@RunWith(Parameterized.class)
public class TestSCMContainerPlacementRackAware {
  private NetworkTopology cluster;
  private ConfigurationSource conf;
  private NodeManager nodeManager;
  private Integer datanodeCount;
  private List<DatanodeDetails> datanodes = new ArrayList<>();
  // policy with fallback capability
  private SCMContainerPlacementRackAware policy;
  // policy prohibit fallback
  private SCMContainerPlacementRackAware policyNoFallback;
  // node storage capacity
  private static final long STORAGE_CAPACITY = 100L;
  private SCMContainerPlacementMetrics metrics;
  private static final int NODE_PER_RACK = 5;

  public TestSCMContainerPlacementRackAware(Integer count) {
    this.datanodeCount = count;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> setupDatanodes() {
    return Arrays.asList(new Object[][] {{3}, {4}, {5}, {6}, {7}, {8}, {9},
        {10}, {11}, {12}, {13}, {14}, {15}});
  }

  @Before
  public void setup() {
    //initialize network topology instance
    conf = new OzoneConfiguration();
    NodeSchema[] schemas = new NodeSchema[]
        {ROOT_SCHEMA, RACK_SCHEMA, LEAF_SCHEMA};
    NodeSchemaManager.getInstance().init(schemas, true);
    cluster = new NetworkTopologyImpl(NodeSchemaManager.getInstance());

    // build datanodes, and network topology
    String rack = "/rack";
    String hostname = "node";
    for (int i = 0; i < datanodeCount; i++) {
      // Totally 3 racks, each has 5 datanodes
      DatanodeDetails node = MockDatanodeDetails.createDatanodeDetails(
          hostname + i, rack + (i / NODE_PER_RACK));
      datanodes.add(node);
      cluster.add(node);
    }

    // create mock node manager
    nodeManager = Mockito.mock(NodeManager.class);
    when(nodeManager.getNodes(NodeState.HEALTHY))
        .thenReturn(new ArrayList<>(datanodes));
    when(nodeManager.getNodeStat(anyObject()))
        .thenReturn(new SCMNodeMetric(STORAGE_CAPACITY, 0L, 100L));
    if (datanodeCount > 4) {
      when(nodeManager.getNodeStat(datanodes.get(2)))
          .thenReturn(new SCMNodeMetric(STORAGE_CAPACITY, 90L, 10L));
      when(nodeManager.getNodeStat(datanodes.get(3)))
          .thenReturn(new SCMNodeMetric(STORAGE_CAPACITY, 80L, 20L));
      when(nodeManager.getNodeStat(datanodes.get(4)))
          .thenReturn(new SCMNodeMetric(STORAGE_CAPACITY, 70L, 30L));
    } else if (datanodeCount > 3) {
      when(nodeManager.getNodeStat(datanodes.get(2)))
          .thenReturn(new SCMNodeMetric(STORAGE_CAPACITY, 90L, 10L));
      when(nodeManager.getNodeStat(datanodes.get(3)))
          .thenReturn(new SCMNodeMetric(STORAGE_CAPACITY, 80L, 20L));
    } else if (datanodeCount > 2) {
      when(nodeManager.getNodeStat(datanodes.get(2)))
          .thenReturn(new SCMNodeMetric(STORAGE_CAPACITY, 84L, 16L));
    }

    // create placement policy instances
    metrics = SCMContainerPlacementMetrics.create();
    policy = new SCMContainerPlacementRackAware(
        nodeManager, conf, cluster, true, metrics);
    policyNoFallback = new SCMContainerPlacementRackAware(
        nodeManager, conf, cluster, false, metrics);
  }

  @Test
  public void chooseNodeWithNoExcludedNodes() throws SCMException {
    // test choose new datanodes for new pipeline cases
    // 1 replica
    int nodeNum = 1;
    List<DatanodeDetails> datanodeDetails =
        policy.chooseDatanodes(null, null, nodeNum, 15);
    Assert.assertEquals(nodeNum, datanodeDetails.size());

    // 2 replicas
    nodeNum = 2;
    datanodeDetails = policy.chooseDatanodes(null, null, nodeNum, 15);
    Assert.assertEquals(nodeNum, datanodeDetails.size());
    Assert.assertTrue(cluster.isSameParent(datanodeDetails.get(0),
        datanodeDetails.get(1)) || (datanodeCount % NODE_PER_RACK == 1));

    //  3 replicas
    nodeNum = 3;
    datanodeDetails = policy.chooseDatanodes(null, null, nodeNum, 15);
    Assert.assertEquals(nodeNum, datanodeDetails.size());
    // requires at least 2 racks for following statement
    assumeTrue(datanodeCount > NODE_PER_RACK &&
        datanodeCount % NODE_PER_RACK > 1);
    Assert.assertTrue(cluster.isSameParent(datanodeDetails.get(0),
        datanodeDetails.get(1)));
    Assert.assertFalse(cluster.isSameParent(datanodeDetails.get(0),
        datanodeDetails.get(2)));
    Assert.assertFalse(cluster.isSameParent(datanodeDetails.get(1),
        datanodeDetails.get(2)));

    //  4 replicas
    nodeNum = 4;
    datanodeDetails = policy.chooseDatanodes(null, null, nodeNum, 15);
    Assert.assertEquals(nodeNum, datanodeDetails.size());
    // requires at least 2 racks and enough datanodes for following statement
    assumeTrue(datanodeCount > NODE_PER_RACK + 1);
    Assert.assertTrue(cluster.isSameParent(datanodeDetails.get(0),
        datanodeDetails.get(1)));
    Assert.assertFalse(cluster.isSameParent(datanodeDetails.get(0),
        datanodeDetails.get(2)));
    Assert.assertFalse(cluster.isSameParent(datanodeDetails.get(1),
        datanodeDetails.get(2)));
  }

  @Test
  public void chooseNodeWithExcludedNodes() throws SCMException {
    // test choose new datanodes for under replicated pipeline
    // 3 replicas, two existing datanodes on same rack
    assumeTrue(datanodeCount > NODE_PER_RACK);
    int nodeNum = 1;
    List<DatanodeDetails> excludedNodes = new ArrayList<>();

    excludedNodes.add(datanodes.get(0));
    excludedNodes.add(datanodes.get(1));
    List<DatanodeDetails> datanodeDetails = policy.chooseDatanodes(
        excludedNodes, null, nodeNum, 15);
    Assert.assertEquals(nodeNum, datanodeDetails.size());
    Assert.assertFalse(cluster.isSameParent(datanodeDetails.get(0),
        excludedNodes.get(0)));
    Assert.assertFalse(cluster.isSameParent(datanodeDetails.get(0),
        excludedNodes.get(1)));

    // 3 replicas, one existing datanode
    nodeNum = 2;
    excludedNodes.clear();
    excludedNodes.add(datanodes.get(0));
    datanodeDetails = policy.chooseDatanodes(
        excludedNodes, null, nodeNum, 15);
    Assert.assertEquals(nodeNum, datanodeDetails.size());
    Assert.assertTrue(cluster.isSameParent(
        datanodeDetails.get(0), excludedNodes.get(0)) ||
        cluster.isSameParent(datanodeDetails.get(0), excludedNodes.get(1)));

    // 3 replicas, two existing datanodes on different rack
    nodeNum = 1;
    excludedNodes.clear();
    excludedNodes.add(datanodes.get(0));
    excludedNodes.add(datanodes.get(5));
    datanodeDetails = policy.chooseDatanodes(
        excludedNodes, null, nodeNum, 15);
    Assert.assertEquals(nodeNum, datanodeDetails.size());
    Assert.assertTrue(cluster.isSameParent(
        datanodeDetails.get(0), excludedNodes.get(0)) ||
        cluster.isSameParent(datanodeDetails.get(0), excludedNodes.get(1)));
  }

  @Test
  public void testFallback() throws SCMException {
    // 5 replicas. there are only 3 racks. policy with fallback should
    // allocate the 5th datanode though it will break the rack rule(first
    // 2 replicas on same rack, others on different racks).
    assumeTrue(datanodeCount > NODE_PER_RACK * 2 &&
        (datanodeCount % NODE_PER_RACK > 1));
    int nodeNum = 5;
    List<DatanodeDetails> datanodeDetails =
        policy.chooseDatanodes(null, null, nodeNum, 15);
    Assert.assertEquals(nodeNum, datanodeDetails.size());
    Assert.assertTrue(cluster.isSameParent(datanodeDetails.get(0),
        datanodeDetails.get(1)));
    Assert.assertFalse(cluster.isSameParent(datanodeDetails.get(0),
        datanodeDetails.get(2)));
    Assert.assertFalse(cluster.isSameParent(datanodeDetails.get(1),
        datanodeDetails.get(2)));
    Assert.assertFalse(cluster.isSameParent(datanodeDetails.get(0),
        datanodeDetails.get(3)));
    Assert.assertFalse(cluster.isSameParent(datanodeDetails.get(2),
        datanodeDetails.get(3)));

    // get metrics
    long totalRequest = metrics.getDatanodeRequestCount();
    long successCount = metrics.getDatanodeChooseSuccessCount();
    long tryCount = metrics.getDatanodeChooseAttemptCount();
    long compromiseCount = metrics.getDatanodeChooseFallbackCount();

    // verify metrics
    Assert.assertEquals(totalRequest, nodeNum);
    Assert.assertEquals(successCount, nodeNum);
    Assert.assertTrue(tryCount > nodeNum);
    Assert.assertTrue(compromiseCount >= 1);
  }

  @Test
  public void testNoFallback() throws SCMException {
    assumeTrue(datanodeCount > (NODE_PER_RACK * 2) &&
        (datanodeCount <= NODE_PER_RACK * 3));
    // 5 replicas. there are only 3 racks. policy prohibit fallback should fail.
    int nodeNum = 5;
    try {
      policyNoFallback.chooseDatanodes(null, null, nodeNum, 15);
      fail("Fallback prohibited, this call should fail");
    } catch (Exception e) {
      assertEquals("SCMException", e.getClass().getSimpleName());
    }

    // get metrics
    long totalRequest = metrics.getDatanodeRequestCount();
    long successCount = metrics.getDatanodeChooseSuccessCount();
    long tryCount = metrics.getDatanodeChooseAttemptCount();
    long compromiseCount = metrics.getDatanodeChooseFallbackCount();

    Assert.assertEquals(totalRequest, nodeNum);
    MatcherAssert.assertThat("Not enough success count", successCount,
        greaterThanOrEqualTo(1L));
    MatcherAssert.assertThat("Not enough try count", tryCount,
        greaterThanOrEqualTo((long) 1L));
    Assert.assertEquals(compromiseCount, 0);
  }

  @Test
  public void chooseNodeWithFavoredNodes() throws SCMException {
    int nodeNum = 1;
    List<DatanodeDetails> excludedNodes = new ArrayList<>();
    List<DatanodeDetails> favoredNodes = new ArrayList<>();

    // no excludedNodes, only favoredNodes
    favoredNodes.add(datanodes.get(0));
    List<DatanodeDetails> datanodeDetails = policy.chooseDatanodes(
        excludedNodes, favoredNodes, nodeNum, 15);
    Assert.assertEquals(nodeNum, datanodeDetails.size());
    Assert.assertEquals(datanodeDetails.get(0).getNetworkFullPath(),
        favoredNodes.get(0).getNetworkFullPath());

    // no overlap between excludedNodes and favoredNodes, favoredNodes can been
    // chosen.
    excludedNodes.clear();
    favoredNodes.clear();
    excludedNodes.add(datanodes.get(0));
    favoredNodes.add(datanodes.get(2));
    datanodeDetails = policy.chooseDatanodes(
        excludedNodes, favoredNodes, nodeNum, 15);
    Assert.assertEquals(nodeNum, datanodeDetails.size());
    Assert.assertEquals(datanodeDetails.get(0).getNetworkFullPath(),
        favoredNodes.get(0).getNetworkFullPath());

    // there is overlap between excludedNodes and favoredNodes, favoredNodes
    // should not be chosen.
    excludedNodes.clear();
    favoredNodes.clear();
    excludedNodes.add(datanodes.get(0));
    favoredNodes.add(datanodes.get(0));
    datanodeDetails = policy.chooseDatanodes(
        excludedNodes, favoredNodes, nodeNum, 15);
    Assert.assertEquals(nodeNum, datanodeDetails.size());
    Assert.assertFalse(datanodeDetails.get(0).getNetworkFullPath()
        .equals(favoredNodes.get(0).getNetworkFullPath()));
  }

  @Test
  public void testNoInfiniteLoop() throws SCMException {
    int nodeNum = 1;

    try {
      // request storage space larger than node capability
      policy.chooseDatanodes(null, null, nodeNum, STORAGE_CAPACITY + 15);
      fail("Storage requested exceeds capacity, this call should fail");
    } catch (Exception e) {
      assertTrue(e.getClass().getSimpleName().equals("SCMException"));
    }

    // get metrics
    long totalRequest = metrics.getDatanodeRequestCount();
    long successCount = metrics.getDatanodeChooseSuccessCount();
    long tryCount = metrics.getDatanodeChooseAttemptCount();
    long compromiseCount = metrics.getDatanodeChooseFallbackCount();

    Assert.assertEquals(totalRequest, nodeNum);
    Assert.assertEquals(successCount, 0);
    MatcherAssert.assertThat("Not enough try", tryCount,
        greaterThanOrEqualTo((long) nodeNum));
    Assert.assertEquals(compromiseCount, 0);
  }

  @Test
  public void testDatanodeWithDefaultNetworkLocation() throws SCMException {
    String hostname = "node";
    List<DatanodeDetails> dataList = new ArrayList<>();
    NetworkTopology clusterMap =
        new NetworkTopologyImpl(NodeSchemaManager.getInstance());
    for (int i = 0; i < 15; i++) {
      // Totally 3 racks, each has 5 datanodes
      DatanodeDetails node = MockDatanodeDetails.createDatanodeDetails(
          hostname + i, null);
      dataList.add(node);
      clusterMap.add(node);
    }
    Assert.assertEquals(dataList.size(), StringUtils.countMatches(
        clusterMap.toString(), NetConstants.DEFAULT_RACK));

    // choose nodes to host 3 replica
    int nodeNum = 3;
    SCMContainerPlacementRackAware newPolicy =
        new SCMContainerPlacementRackAware(nodeManager, conf, clusterMap, true,
            metrics);
    List<DatanodeDetails> datanodeDetails =
        newPolicy.chooseDatanodes(null, null, nodeNum, 15);
    Assert.assertEquals(nodeNum, datanodeDetails.size());
    Assert.assertTrue(cluster.isSameParent(datanodeDetails.get(0),
        datanodeDetails.get(1)));
    Assert.assertTrue(cluster.isSameParent(datanodeDetails.get(0),
        datanodeDetails.get(2)));
    Assert.assertTrue(cluster.isSameParent(datanodeDetails.get(1),
        datanodeDetails.get(2)));
  }
}
