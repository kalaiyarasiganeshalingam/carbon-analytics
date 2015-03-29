/*
*  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
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
package org.wso2.carbon.analytics.datasource.hbase;

import com.google.common.collect.Lists;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.wso2.carbon.analytics.datasource.commons.Record;
import org.wso2.carbon.analytics.datasource.commons.exception.AnalyticsException;
import org.wso2.carbon.analytics.datasource.commons.exception.AnalyticsTableNotAvailableException;
import org.wso2.carbon.analytics.datasource.core.util.GenericUtils;
import org.wso2.carbon.analytics.datasource.hbase.util.HBaseAnalyticsDSConstants;
import org.wso2.carbon.analytics.datasource.hbase.util.HBaseRuntimeException;
import org.wso2.carbon.analytics.datasource.hbase.util.HBaseUtils;

import java.io.IOException;
import java.util.*;

public class HBaseRecordIterator implements Iterator<Record> {

    private List<String> columns;
    private List<List<String>> batchedIds;

    private int tenantId, totalBatches, currentBatchIndex;

    private boolean fullyFetched;
    private String tableName;
    private Table table;
    private Iterator<Record> subIterator = Collections.emptyIterator();

    public HBaseRecordIterator(int tenantId, String tableName, List<String> columns, List<String> recordIds,
                               Connection conn, int batchSize) throws AnalyticsException, AnalyticsTableNotAvailableException {
        this.init(conn, tenantId, tableName, columns);
        if (batchSize <= 0) {
            throw new AnalyticsException("Error batching records: the batch size should be a positive integer");
        } else {
            this.batchedIds = Lists.partition(recordIds, batchSize);
            this.totalBatches = this.batchedIds.size();
            /* pre-fetching from HBase and populating records for the first time */
            this.preFetch();
        }
    }

    @Override
    public boolean hasNext() {
        boolean hasMore = this.subIterator.hasNext();
        if (!hasMore) {
            try {
                this.preFetch();
            } catch (AnalyticsTableNotAvailableException e) {
                this.subIterator = Collections.emptyIterator();
            }
        }
        return this.subIterator.hasNext();
    }

    @Override
    public Record next() {
        if (this.hasNext()) {
            return this.subIterator.next();
        } else {
            this.cleanup();
            throw new NoSuchElementException("No further elements exist in iterator");
        }
    }

    @Override
    public void remove() {
            /* nothing to do here, since this is a read-only iterator */
    }

    private void preFetch() throws AnalyticsTableNotAvailableException {
        if (fullyFetched || this.totalBatches == 0) {
            return;
        }
        List<String> currentBatch = this.batchedIds.get(this.currentBatchIndex);
        List<Record> fetchedRecords = new ArrayList<>();
        List<Get> gets = new ArrayList<>();
        Set<String> colSet = null;
        Map<String, Object> values;
        long timestamp;

        for (String currentId : currentBatch) {
            Get get = new Get(Bytes.toBytes(currentId));
            /* if the list of columns to be retrieved is null, retrieve ALL columns. */
            if (this.columns != null && this.columns.size() > 0) {
                colSet = new HashSet<>(this.columns);
                get.addColumn(HBaseAnalyticsDSConstants.ANALYTICS_DATA_COLUMN_FAMILY_NAME,
                        HBaseAnalyticsDSConstants.ANALYTICS_ROWDATA_QUALIFIER_NAME);
            } else {
                get.addFamily(HBaseAnalyticsDSConstants.ANALYTICS_DATA_COLUMN_FAMILY_NAME);
            }
            gets.add(get);
        }
        try {
            Result[] results = this.table.get(gets);
            for (Result currentResult : results) {
                byte[] rowId = currentResult.getRow();
                if (currentResult.containsColumn(HBaseAnalyticsDSConstants.ANALYTICS_DATA_COLUMN_FAMILY_NAME,
                        HBaseAnalyticsDSConstants.ANALYTICS_ROWDATA_QUALIFIER_NAME)) {
                    Cell dataCell = currentResult.getColumnLatestCell
                            (HBaseAnalyticsDSConstants.ANALYTICS_DATA_COLUMN_FAMILY_NAME,
                                    HBaseAnalyticsDSConstants.ANALYTICS_ROWDATA_QUALIFIER_NAME);
                    values = GenericUtils.decodeRecordValues(CellUtil.cloneValue(dataCell), colSet);
                    timestamp = dataCell.getTimestamp();
                    fetchedRecords.add(new Record(new String(rowId), this.tenantId, this.tableName, values, timestamp));
                } else if (currentResult.containsColumn(HBaseAnalyticsDSConstants.ANALYTICS_DATA_COLUMN_FAMILY_NAME,
                        HBaseAnalyticsDSConstants.ANALYTICS_TS_QUALIFIER_NAME)) {
                    Cell timeCell = currentResult.getColumnLatestCell
                            (HBaseAnalyticsDSConstants.ANALYTICS_DATA_COLUMN_FAMILY_NAME,
                                    HBaseAnalyticsDSConstants.ANALYTICS_TS_QUALIFIER_NAME);
                    values = new HashMap<>();
                    timestamp = HBaseUtils.decodeLong(CellUtil.cloneValue(timeCell));
                    fetchedRecords.add(new Record(new String(rowId), this.tenantId, this.tableName, values, timestamp));
                }
                    /* else, No valid data in row, ignore.*/
            }
            this.subIterator = fetchedRecords.iterator();
        } catch (Exception e) {
            if (e instanceof RetriesExhaustedException) {
                throw new AnalyticsTableNotAvailableException(tenantId, tableName);
            }
            this.cleanup();
            throw new HBaseRuntimeException("Error reading data from table " + this.tableName + " for tenant " +
                    this.tenantId, e);
        }
        this.currentBatchIndex++;
        if ((this.totalBatches == 1) || this.currentBatchIndex == this.totalBatches - 1) {
            this.fullyFetched = true;
        }
    }

    private void init(Connection conn, int tenantId, String tableName, List<String> columns) throws AnalyticsException {
        this.tenantId = tenantId;
        this.tableName = tableName;
        this.columns = columns;
        try {
            this.table = conn.getTable(TableName.valueOf(
                    HBaseUtils.generateTableName(tenantId, tableName, HBaseAnalyticsDSConstants.DATA)));
        } catch (IOException e) {
            throw new AnalyticsException("The table " + tableName + " for tenant " + tenantId +
                    " could not be initialized for reading: " + e.getMessage(), e);
        }
    }

    private void cleanup() {
        try {
            this.table.close();
        } catch (IOException ignore) {
            /* do nothing, the connection is dead anyway */
        }
    }

}