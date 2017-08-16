package org.mskcc.limsrest.web;

import java.util.concurrent.Future;
import java.util.LinkedList;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

import org.mskcc.limsrest.limsapi.*;
import org.mskcc.limsrest.connection.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;



@RestController
public class BillingReport {

    private final ConnectionQueue connQueue; 
    private final GetBillingReport task;
    private Log log = LogFactory.getLog(BillingReport.class);
   
    public BillingReport( ConnectionQueue connQueue, GetBillingReport getBillingReport){
        this.connQueue = connQueue;
        this.task = getBillingReport;
    }

    @RequestMapping("/getBillingReport")
    public LinkedList<RunSummary> getContent(@RequestParam(value="project", required=true) String proj) {

       RunSummary rs = new RunSummary("BLANK_RUN", "BLANK_REQUEST");
       LinkedList<RunSummary> runSums = new LinkedList<>();
       log.info("Starting get billing report for project " + proj);
       task.init(proj);
       Future<Object> result = connQueue.submitTask(task);
       try{
         runSums = (LinkedList<RunSummary>)result.get();
       } catch(Exception e){
         StringWriter sw = new StringWriter();
         PrintWriter pw = new PrintWriter(sw);
         e.printStackTrace(pw);
         rs.setInvestigator(e.getMessage() + " TRACE: " + sw.toString());
         runSums.add(rs);
       }
       return runSums;
   }

}
