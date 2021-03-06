package org.mskcc.limsrest.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.velox.sloan.cmo.recmodels.RequestModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class RequestSummary {
    private ArrayList<SampleSummary> samples;
    private LinkedList<Long> deliveryDates;
    private Long recentDeliveryDate;
    private Long completedDate;
    private String cmoProjectId;
    private String requestId;
    private String requestType;
    private String investigator;
    private String pi;
    private String investigatorEmail;
    private String piEmail;
    private String projectManager;
    private String analysisType;
    private boolean pipelinable;
    private boolean analysisRequested;
    private Boolean isCmoRequest = Boolean.FALSE;
    private long recordId;
    private Long receivedDate;
    private Long dueDate;
    private Short sampleNumber;
    private String restStatus;
    private String specialDelivery;
    private String labHeadEmail;
    private String qcAccessEmail;
    private String dataAccessEmails;
    private Boolean isIgoComplete;
    private List<String> runFolders;

    public RequestSummary() {
        this("UNKNOWN");
    }

    public RequestSummary(String request) {
        samples = new ArrayList<>();
        deliveryDates = new LinkedList<>();
        requestId = request;
        investigator = "UNKNOWN";
        restStatus = "SUCCESS";
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public boolean getAutorunnable() {
        return pipelinable;
    }

    public Boolean getIsCmoRequest() { return isCmoRequest; }

    public void setIsCmoRequest(Boolean cmoRequest) {
        if (cmoRequest == null)
            isCmoRequest = Boolean.FALSE;
        else {
            this.isCmoRequest = cmoRequest;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public List<String> getRunFolders() {
        return runFolders;
    }

    public void setRunFolders(List<String> runFolders) {
        this.runFolders = runFolders;
    }


    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getDataAccessEmails() {
        return dataAccessEmails;
    }

    public void setDataAccessEmails(String dataAccessEmails) {
        this.dataAccessEmails = dataAccessEmails;
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Long getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(Long receivedDate) {
        this.receivedDate = receivedDate;
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Long getDueDate() {
        return dueDate;
    }

    public void setDueDate(Long dueDate) {
        this.dueDate = dueDate;
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getQcAccessEmail() {
        return qcAccessEmail;
    }

    public void setQcAccessEmail(String qcAccessEmail) {
        this.qcAccessEmail = qcAccessEmail;
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getLabHeadEmail() {
        return labHeadEmail;
    }

    public void setLabHeadEmail(String labHeadEmail) {
        this.labHeadEmail = labHeadEmail;
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Boolean getIsIgoComplete() {
        return this.isIgoComplete;
    }

    public void setIsIgoComplete(Boolean isIgoComplete) {
        this.isIgoComplete = isIgoComplete;
    }

    public void setRestStatus(String s) {
        this.restStatus = s;
    }

    public void setSpecialDelivery(String s) {
        this.specialDelivery = s;
    }

    public ArrayList<SampleSummary> getSamples() {
        return samples;
    }

    public void addSample(SampleSummary s) {
        samples.add(s);
    }

    public void addDeliveryDate(Long date) {
        deliveryDates.add(date);
    }

    public void setAnalysisRequested(boolean req) {
        this.analysisRequested = req;
    }

    public void setAnalysisType(String s) { this.analysisType = s;  }

    public void setRecordId(long id) {
        this.recordId = id;
    }

    public void setRequestType(String s) {
        this.requestType = s;
    }

    public void setCmoProject(String proj) {
        cmoProjectId = proj;
    }

    public void setPi(String name) {
        pi = name;
    }

    public void setProjectManager(String name) {
        projectManager = name;
    }

    public void setAutorunnable(boolean pipelinable) {
        this.pipelinable = pipelinable;
    }

    public void setInvestigator(String name) {
        investigator = name;
    }

    public void setPiEmail(String email) {
        piEmail = email;
    }

    public void setInvestigatorEmail(String email) {
        investigatorEmail = email;
    }

    public void setSampleNumber(Short sampleNumber) {
        this.sampleNumber = sampleNumber;
    }

    public List<Long> getDeliveryDate() {
        Collections.sort(deliveryDates);
        return deliveryDates;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Long getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(Long completedDate) {
        this.completedDate = completedDate;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Long getRecentDeliveryDate() {
        return recentDeliveryDate;
    }

    public void setRecentDeliveryDate(Long mostRecentDeliveryDate) {
        this.recentDeliveryDate = mostRecentDeliveryDate;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public boolean getAnalysisRequested() {
        return analysisRequested;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getAnalysisType() { return analysisType; }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public long getRecordId() {
        return recordId;
    }

    public String getRequestId() {
        return requestId;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getCmoProject() {
        return cmoProjectId;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getPi() {
        return pi;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getInvestigator() {
        return investigator;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getPiEmail() {
        return piEmail;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getProjectManager() {
        return projectManager;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getInvestigatorEmail() {
        return investigatorEmail;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Short getSampleNumber() {
        return sampleNumber;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getSpecialDelivery() {
        return specialDelivery;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getRequestType() {
        return requestType;
    }

    public String getRestStatus() {
        return this.restStatus;
    }

    public static RequestSummary errorMessage(String e) {
        RequestSummary rs = new RequestSummary("ERROR");
        SampleSummary ss = new SampleSummary();
        ss.addRequest(e);
        rs.addSample(ss);
        return rs;
    }
}