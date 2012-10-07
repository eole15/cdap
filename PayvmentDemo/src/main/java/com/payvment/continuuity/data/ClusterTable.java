package com.payvment.continuuity.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.continuuity.api.data.BatchCollectionRegistry;
import com.continuuity.api.data.DataFabric;
import com.continuuity.api.data.Delete;
import com.continuuity.api.data.OperationException;
import com.continuuity.api.data.OperationResult;
import com.continuuity.api.data.ReadColumnRange;
import com.continuuity.api.data.Write;
import com.continuuity.api.data.lib.DataLib;
import com.continuuity.api.data.util.Bytes;

public class ClusterTable extends DataLib {

  private static final String CLUSTER_TABLE = "ClusterTable";

  public ClusterTable(DataFabric fabric, BatchCollectionRegistry registry) {
    super(CLUSTER_TABLE, fabric, registry);
  }

  public void resetClusters(int maxClusterNumber) {
    for (int i=1;i<=maxClusterNumber;i++) {
      this.collector.add(new Delete(makeRow(i)));
    }
  }

  //
  // Fine grained schema
  //

  /**
   * Reads the cluster information for the specified cluster id.  Returns null
   * if no cluster information found.
   * <p>
   * This operation is synchronous.
   * @param clusterId
   * @return cluster info containing map from classification/category to weight
   * @throws OperationException
   */
  public Map<String,Double> readCluster(int clusterId)
      throws OperationException {
    OperationResult<Map<byte[],byte[]>> result =
        this.fabric.read(new ReadColumnRange(makeRow(clusterId), null));
    if (result.isEmpty()) return null;
    Map<byte[],byte[]> map = result.getValue();
    Map<String,Double> ret = new TreeMap<String,Double>();
    for (Map.Entry<byte[],byte[]> entry : map.entrySet()) {
      ret.put(Bytes.toString(entry.getKey()), Bytes.toDouble(entry.getValue()));
    }
    return ret;
  }

  /**
   * Writes the specified cluster information for the specified cluster id.
   * <p>
   * This operation is asynchronous and will be performed as part of the flowlet
   * process batch.
   * @param clusterId id of cluster
   * @param clusterInfo map of category name to weight in cluster
   */
  public void writeCluster(int clusterId, Map<String,Double> clusterInfo) {
    int len = clusterInfo.size();
    List<byte[]> strings = new ArrayList<byte[]>(len);
    List<byte[]> doubles = new ArrayList<byte[]>(len);
    for (Map.Entry<String,Double> info : clusterInfo.entrySet()) {
      strings.add(Bytes.toBytes(info.getKey()));
      doubles.add(Bytes.toBytes(info.getValue().doubleValue()));
    }
    this.collector.add(new Write(makeRow(clusterId),
        strings.toArray(new byte[len][]), doubles.toArray(new byte[len][])));
  }

  /**
   * Writes the specified category and weight into the specified cluster.
   * <p>
   * Updates any existing weight for this category and cluster.
   * <p>
   * This operation is asynchronous and will be performed as part of the flowlet
   * process batch.
   * @param clusterId
   * @param category
   * @param weight
   */
  public void writeCluster(int clusterId, String category, Double weight) {
    this.collector.add(new Write(makeRow(clusterId), Bytes.toBytes(category),
        Bytes.toBytes(weight)));
  }

  //
  // Coarse grained schema (keep switching between this and above)
  //
  //  private static final byte [] COLUMN = Bytes.toBytes("c");
  //
  //  /**
  //   * Reads the cluster information for the specified cluster id.  Returns null
  //   * if no cluster information found.
  //   * <p>
  //   * This operation is synchronous.
  //   * @param clusterId
  //   * @return cluster info containing map from classification/category to weight
  //   * @throws OperationException
  //   */
  //  public Map<String,Double> readCluster(int clusterId)
  //      throws OperationException {
  //    OperationResult<Map<byte[],byte[]>> result =
  //        this.fabric.read(new Read(makeRow(clusterId), COLUMN));
  //    if (result.isEmpty()) return null;
  //    Map<byte[],byte[]> map = result.getValue();
  //    Cluster cluster = Cluster.fromBytes(map.get(COLUMN));
  //    return cluster.getClusterInfo();
  //  }
  //
  //  /**
  //   * Writes the specified cluster information for the specified cluster id.
  //   * <p>
  //   * This operation is asynchronous and will be performed as part of a flowlet
  //   * process batch.
  //   * @param clusterid
  //   * @param clusterInfo
  //   */
  //  public void writeCluster(int clusterId, Map<String,Double> clusterInfo) {
  //    this.collector.add(new Write(makeRow(clusterId), COLUMN,
  //        new Cluster(clusterId, clusterInfo).toBytes()));
  //  }
  
  public static byte [] makeRow(int clusterid) {
    return Bytes.add(Bytes.toBytes(CLUSTER_TABLE), Bytes.toBytes(clusterid));
  }
}
