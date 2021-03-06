package org.mskcc.limsrest.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mskcc.limsrest.ConnectionPoolLIMS;
import org.mskcc.limsrest.service.interops.GetInterOpsDataTask;
import org.mskcc.util.Constants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/")
public class GetInterOpsData {
    private static final Log log = LogFactory.getLog(GetInterOpsData.class);

    private final ConnectionPoolLIMS conn;

    public GetInterOpsData(ConnectionPoolLIMS conn) {
        this.conn = conn;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/getInterOpsData")
    public ResponseEntity<List<Map<String, String>>> getInterOps(@RequestParam(value = "runId") String runId) {
        List<Map<String, String>> interOps = new ArrayList<>();

        log.info("Starting get /getInterOpsData " + runId);
        try {
            GetInterOpsDataTask task = new GetInterOpsDataTask();
            task.init(runId);
            Future<Object> result = conn.submitTask(task);
            interOps = (List<Map<String, String>>) result.get();
        } catch (Exception e) {
            log.error(String.format("Error while retrieving results for getInterOpsData run id: %s", runId), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).header(Constants.ERROR, e.getMessage()).body(interOps);
        }

        return ResponseEntity.ok(interOps);
    }
}