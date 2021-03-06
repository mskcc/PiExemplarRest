package org.mskcc.limsrest.service;

import com.velox.api.datarecord.DataRecord;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.LinkedList;
import java.util.List;

/**
 * Returns the list of all LIMS barcodes.
 * 
 * @author Aaron Gabow
 */
public class GetBarcodeInfo extends LimsTask {
    private static Log log = LogFactory.getLog(GetBarcodeInfo.class);

    @PreAuthorize("hasRole('READ')")
    @Override
    public Object execute(VeloxConnection conn) {
        List<BarcodeSummary> bcodes = new LinkedList<>();

        try {
            List<DataRecord> indexList = dataRecordManager.queryDataRecords("IndexAssignment", "IndexType != 'IDT_TRIM' ORDER BY IndexType, IndexId", user);
            for (DataRecord i : indexList) {
                try {
                    bcodes.add(new BarcodeSummary(i.getStringVal("IndexType", user), i.getStringVal("IndexId", user), i.getStringVal("IndexTag", user)));
                } catch (NullPointerException npe) {
                }
            }
        } catch (Throwable e) {
            log.error("ERROR IN SETTING REQUEST STATUS: " + e.getMessage(), e);
        }

        return bcodes;
    }
}