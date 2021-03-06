package org.mskcc.limsrest.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mskcc.limsrest.ConnectionPoolLIMS;
import org.mskcc.limsrest.service.ListStudies;
import org.mskcc.limsrest.service.ProjectSummary;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/")
public class GetAllStudies {
    private static Log log = LogFactory.getLog(GetAllStudies.class);

    private final ConnectionPoolLIMS conn;

    public GetAllStudies(ConnectionPoolLIMS conn) {
        this.conn = conn;
    }

    @RequestMapping("/getAllStudies")
    public List<ProjectSummary> getContent(@RequestParam(value = "cmoOnly", defaultValue = "NULL") String cmoOnly) {
        log.info("Starting /getAllStudies");
        ListStudies task = new ListStudies();
        task.init(cmoOnly);
        Future<Object> result = conn.submitTask(task);
        try {
            List<ProjectSummary> r = (List<ProjectSummary>) result.get();
            return r;
        } catch (Exception e) {
            ProjectSummary errorProj = new ProjectSummary();
            errorProj.setCmoProjectId(e.getMessage());
            List<ProjectSummary> ps = new LinkedList<>();
            ps.add(errorProj);
            return ps;
        }
    }
}