package org.mskcc.limsrest.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mskcc.limsrest.connection.ConnectionQueue;
import org.mskcc.limsrest.limsapi.dmp.DateRetriever;
import org.mskcc.limsrest.limsapi.dmp.DefaultTodayDateRetriever;
import org.mskcc.limsrest.limsapi.dmp.GenerateBankedSamplesFromDMP;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/")
public class CreateBankedSamplesFromDMP {
    private static final Log log = LogFactory.getLog(CreateBankedSamplesFromDMP.class);

    private final ConnectionQueue connQueue;
    private final GenerateBankedSamplesFromDMP task = new GenerateBankedSamplesFromDMP();
    private final DateRetriever dateRetriever = new DefaultTodayDateRetriever();

    public CreateBankedSamplesFromDMP(ConnectionQueue connQueue) {
        this.connQueue = connQueue;
    }

    @RequestMapping("/createBankedSamplesFromDMP")
    public ResponseEntity<String> getSampleCmoId(@RequestParam(value = "date", required = false) String date) {
        LocalDate localDate = null;
        try {
            localDate = dateRetriever.retrieve(date);
            log.info(String.format("Starting to create banked samples from DMP samples for date: %s", localDate));
            log.info("Creating task");

            task.setDate(localDate);

            log.info("Getting result");
            Future<Object> result = connQueue.submitTask(task);

            String response = (String) result.get();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            String message = String.format("Unable to create Banked Samples from DMP Samples for date: %s",
                    getDateUsed(date, localDate));
            log.error(message, e);

            ResponseEntity<String> responseEntity = new ResponseEntity<>(message + " Cause: " + e.getMessage(),
                    HttpStatus.NOT_FOUND);
            return responseEntity;
        }
    }

    private Object getDateUsed(@RequestParam(value = "date", required = false) String date, LocalDate localDate) {
        if (localDate == null) {
            if (date == null)
                return "no date provided and unable to resolve default date";
            return date;
        }

        return localDate;
    }
}