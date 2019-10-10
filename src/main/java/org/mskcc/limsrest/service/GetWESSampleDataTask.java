package org.mskcc.limsrest.service;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.mskcc.limsrest.service.sampletracker.WESSampleData;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
@Service
public class GetWESSampleDataTask extends LimsTask {
    private final List<String> HISEQ_2500_MACHINE_NAMES = Arrays.asList("KIM", "MOMO");
    private final List<String> HISEQ_4000_MACHINE_NAMES = Arrays.asList("PITT", "JAX");
    private final List<String> MISEQ_MACHINE_NAMES = Arrays.asList("AYYAN", "JOHNSAWYERS", "TOMS", "VIC");
    private final List<String> NOVASEQ_MACHINE_NAMES = Arrays.asList("MICHELLE", "DIANA");
    private final List<String> NEXTSEQ_MACHINE_NAMES = Arrays.asList("SCOTT");
    private final List<String> VALID_RECIPES = Arrays.asList("wholeexomesequencing");
    private Log log = LogFactory.getLog(GetWESSampleDataTask.class);
    private String timestamp;
    DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public void init(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public Object execute(VeloxConnection conn) {
        long start = System.currentTimeMillis();
        SimpleDateFormat df2 = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        try {
            log.info(" Starting GetWesSample task using timestamp " + timestamp);
            List<DataRecord> dmpTrackerRecords = new ArrayList<>();
            try {
                dmpTrackerRecords = dataRecordManager.queryDataRecords("DMPSampleTracker", "DateCreated > " + Long.parseLong(timestamp) + " AND i_SampleDownstreamApplication IN ('Whole Exome Sequencing')", user);
                log.info("Num dmpTracker Records: " + dmpTrackerRecords.size());
            } catch (Throwable e) {
                log.error(e.getMessage(), e);
                return null;
            }
            List<WESSampleData> resultList = new ArrayList<>();
            JSONObject consentAList = getConsentStatus("parta");
            JSONObject consentCList = getConsentStatus("partc");
            if (!dmpTrackerRecords.isEmpty()) {
                for (DataRecord dmpTrackRec : dmpTrackerRecords) {
                    List<DataRecord> sampleCmoInfoRecs = new ArrayList<>();
                    if(dmpTrackRec.getValue("i_StudySampleIdentifierInvesti", user) != null){
                        sampleCmoInfoRecs = dataRecordManager.queryDataRecords("SampleCMOInfoRecords", "OtherSampleId = '" + dmpTrackRec.getStringVal("i_StudySampleIdentifierInvesti", user) + "'", user);
                    }
                    if (sampleCmoInfoRecs.size() > 0){
                        Object investigatorSampleId = dmpTrackRec.getValue("i_StudySampleIdentifierInvesti",user);
                        for (DataRecord cmoInfoRec : sampleCmoInfoRecs){
                            Object cmoInfoOtherSampleId = cmoInfoRec.getValue("OtherSampleId", user);
                            Object tumorOrNormal = cmoInfoRec.getValue("TumorOrNormal", user);
                            if (!StringUtils.isBlank(cmoInfoOtherSampleId.toString().replace(" ", "")) && tumorOrNormal !=null && cmoInfoOtherSampleId.toString().equals(investigatorSampleId.toString())
                                && cmoInfoRec.getParentsOfType("Sample", user).size() > 0) {
                                log.info("cmoInfo othersampleId: " + cmoInfoOtherSampleId.toString() + " " + cmoInfoRec.getParentsOfType("Sample", user).size());
                                DataRecord sample = cmoInfoRec.getParentsOfType("Sample", user).get(0);
                                if (isValidRecipeToProcess(sample)) {
                                    DataRecord request = getRelatedRequest(sample);
                                    String sampleId = sample.getStringVal("SampleId", user);
                                    String userSampleId = sample.getStringVal("UserSampleID", user);
                                    String cmoSampleId = cmoInfoRec.getStringVal("CorrectedCMOID", user);
                                    String cmoPatientId = cmoInfoRec.getStringVal("CmoPatientId", user);
                                    String dmpSampleId = dmpTrackRec.getStringVal("i_DMPSampleID", user);
                                    JSONObject cvrData = getCvrData(dmpSampleId);
                                    String dmpPatientId = getCvrDataValue(cvrData, "dmp_patient_lbl");
                                    String mrn = getCvrDataValue(cvrData, "mrn");
                                    String sex = getCvrDataValue(cvrData, "gender");
                                    String sampleType = getCvrDataValue(cvrData, "sample_type");
                                    String sampleClass = getCvrDataValue(cvrData, "sample_type");
                                    String tumorType = getCvrDataValue(cvrData, "tumor_type");
                                    String parentalTumorType = getOncotreeType(tumorType);
                                    String tissueSite = getCvrDataValue(cvrData, "primary_site");
                                    String molAccessionNum = getCvrDataValue(cvrData, "molecular_accession_num");
                                    String dateDmpRequest = (String) getValueFromDataRecord(dmpTrackRec, "i_DateSubmittedtoDMP", "Date");
                                    String dmpRequestId = dmpTrackRec.getStringVal("i_RequestReference", user);
                                    String igoRequestId = (String)getValueFromDataRecord(request, "RequestId", "String");
                                    String collectionYear = (String) getValueFromDataRecord(cmoInfoRec, "CollectionYear", "String");
                                    String dateIgoReceived = (String) getValueFromDataRecord(request, "ReceivedDate", "Date");
                                    String igoCompleteDate = (String) getValueFromDataRecord(request, "CompletedDate", "Date");
                                    String applicationRequested = (String) getValueFromDataRecord(dmpTrackRec, "i_SampleDownstreamApplication", "String");
                                    String baitsetUsed = getWesBaitsetType(sample);
                                    String sequencerType = getSequencerTypeUsed(sample);
                                    String projectTitle = (String) getValueFromDataRecord(dmpTrackRec, "i_Studyname", "String");
                                    String labHead = (String) getValueFromDataRecord(request, "LaboratoryHead", "String");
                                    String ccFund = (String) getValueFromDataRecord(dmpTrackRec, "i_FundCostCenter", "String");
                                    String scientificPi = " ";
                                    Boolean consentPartAStatus = consentAList.getBoolean("P-0038146");
                                    Boolean consentPartCStatus = consentCList.getBoolean("P-0038146");
                                    String sampleStatus = getLatestIGOStatus(sample, request.getStringVal("RequestId", user));
                                    String accessLevel = "";
                                    String clinicalTrial = "";
                                    String sequencingSite = "";
                                    String piRequestDate = "";
                                    String pipeline = "";
                                    String tissueType = "";
                                    String collaborationCenter = "";
                                    String limsRecordId = String.valueOf(sample.getLongVal("RecordId", user));
                                    resultList.add(new WESSampleData(sampleId, userSampleId, cmoSampleId, cmoPatientId, dmpSampleId, dmpPatientId, mrn, sex, sampleType, sampleClass, tumorType, parentalTumorType, tissueSite,
                                            molAccessionNum, collectionYear, dateDmpRequest, dmpRequestId, igoRequestId, dateIgoReceived, igoCompleteDate, applicationRequested, baitsetUsed, sequencerType, projectTitle, labHead, ccFund, scientificPi,
                                            consentPartAStatus, consentPartCStatus, sampleStatus, accessLevel, clinicalTrial, sequencingSite, piRequestDate, pipeline, tissueType, collaborationCenter, limsRecordId));
                                }
                            }
                        }
                    }
                }
            }
            log.info("Results found: " + resultList.size() + " Elapsed time (ms): " + (System.currentTimeMillis() - start));
            return resultList;
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private DataRecord getRelatedRequest( DataRecord sample) throws RemoteException, NotFound {
        try {
            if (sample.getParentsOfType("Request", user).size() > 0) {
                return sample.getParentsOfType("Request", user).get(0);
            }
            Stack <DataRecord> sampleStack = new Stack<>();
            if (sample.getParentsOfType("Sample", user).size()>0){
                sampleStack.push(sample.getParentsOfType("Sample", user).get(0));
            }
            do{
                DataRecord startSample = sampleStack.pop();
                if (startSample.getParentsOfType("Request", user).size() > 0){
                    return startSample.getParentsOfType("Request", user).get(0);
                }
                else if(startSample.getParentsOfType("Sample", user).size()>0){
                    sampleStack.push(startSample.getParentsOfType("Sample", user).get(0));
                }
            }while(!sampleStack.isEmpty());
        }catch (Exception e) {
            log.error(String.format("Error occured while finding parent Request for Sample %s\n%s", sample.getStringVal("SampleId", user), Arrays.toString(e.getStackTrace())));
        }
        return null;
    }

    private Boolean isValidRecipeToProcess(DataRecord sample) throws NotFound, RemoteException {
        String sampleId = sample.getStringVal("SampleId", user);
        try {
            Object recipe = sample.getValue("Recipe", user);
            if (recipe !=null && VALID_RECIPES.contains(String.valueOf(recipe).toLowerCase())){
                return true;
            }
        } catch (Exception e) {
            log.error(String.format("Error occured while validating Recipe for Sample %s\n%s", sampleId, Arrays.toString(e.getStackTrace())));
        }
        return false;
    }

    private Object getValueFromDataRecord(DataRecord record, String fieldName, String fieldType) throws NotFound, RemoteException {
        if (record == null){
            return "";
        }
        if (record.getValue(fieldName, user) != null){
            if (fieldType.equals("String")){
                return record.getStringVal(fieldName, user);
            }
            if (fieldType.equals("Integer")){
                return record.getIntegerVal(fieldName, user);
            }
            if (fieldType.equals("Long")){
                return record.getLongVal(fieldName, user);
            }
            if (fieldType.equals("Double")){
                return record.getDoubleVal(fieldName, user);
            }
            if (fieldType.equals("Date")){
                SimpleDateFormat df2 = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                return df2.format(new Date(record.getDateVal(fieldName, user)));
            }
        }
        return "";
    }

    private JSONObject getCvrData(String dmpSampleId){
        StringBuffer response = new StringBuffer();
        JSONObject cvrResponseData = new JSONObject() ;
        try {
            URL url = new URL("http://draco.mskcc.org:9890/get_cmo_metadata/" + dmpSampleId.replace(" ", "%20"));
            log.info(url.toString());
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            con.disconnect();
            cvrResponseData = new JSONObject(response.toString());
        } catch (Exception e) {
            log.error(String.format("Error occured while querying CVR end point for DMP Sample ID %s\n%s", dmpSampleId, Arrays.toString(e.getStackTrace())));
        }
        return cvrResponseData;
    }

    private String getCvrDataValue(JSONObject cvrResponseData, String key){
        if (cvrResponseData.length() > 0){
            if(cvrResponseData.has(key) && cvrResponseData.get(key)!=null){
                return (String)cvrResponseData.get(key);
            }
        }
        return "Not found";
    }
    private String getOncotreeType(String cancerType){
        StringBuffer response = new StringBuffer();
        JSONObject oncotreeResponseData = new JSONObject();
        String mainTumorType = "";
        try {
            URL url = new URL("http://oncotree.mskcc.org/api/tumorTypes/search/name/" + cancerType.replace(" ", "%20") + "?exactMatch=false");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            con.disconnect();
            oncotreeResponseData = new JSONObject(response.toString());
        } catch (Exception e) {
            log.error(String.format("Error occured while querying oncotree end point for Tumor Type %s\n%s", cancerType, Arrays.toString(e.getStackTrace())));
            return "";
        }
        if(oncotreeResponseData.length()>0){
            try {
                mainTumorType = oncotreeResponseData.getString("mainType");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        else {
            mainTumorType = "Oncotree Tumor Type not found.";
        }
        return mainTumorType;
    }

    private String getWesBaitsetType(DataRecord sample) throws NotFound, RemoteException {
        try {
            if (sample.getChildrenOfType("KAPAAgilentCaptureProtocol2", user).length > 0) {
                DataRecord kapaProtocol = sample.getChildrenOfType("KAPAAgilentCaptureProtocol2", user)[0];
                if (kapaProtocol.getValue("AgilentCaptureBaitSet", user) !=null){
                    return kapaProtocol.getStringVal("AgilentCaptureBaitSet", user);
                }
            }
            Stack <DataRecord> sampleStack = new Stack<>();
            if (sample.getChildrenOfType("Sample", user).length >0){
                sampleStack.push(sample.getChildrenOfType("Sample", user)[0]);
            }
            do{
                DataRecord startSample = sampleStack.pop();
                if (startSample.getChildrenOfType("KAPAAgilentCaptureProtocol2", user).length > 0){
                    DataRecord kapaProtocol = startSample.getChildrenOfType("KAPAAgilentCaptureProtocol2", user)[0];
                    if (kapaProtocol.getValue("AgilentCaptureBaitSet", user) !=null){
                        return kapaProtocol.getStringVal("AgilentCaptureBaitSet", user);
                    }
                }
                else if(startSample.getChildrenOfType("KAPAAgilentCaptureProtocol2", user).length > 0){
                    sampleStack.push(startSample.getChildrenOfType("KAPAAgilentCaptureProtocol2", user)[0]);
                }
            }while(!sampleStack.isEmpty());
        }catch (Exception e) {
            log.error(String.format("Error occured while finding parent Request for Sample %s\n", sample.getStringVal("SampleId", user), Arrays.toString(e.getStackTrace())));
        }
        return "";
    }

   private String getSequencerTypeUsed(DataRecord sample) throws RemoteException, NotFound {
        List<DataRecord> sampleLevelSeqQcRecs = sample.getDescendantsOfType("SeqAnalysisSampleQC", user);
        log.info("SeqAnalysisRecords: " + sampleLevelSeqQcRecs.size());
        Set<String> sequencerTypes = new HashSet<>();
        if (sampleLevelSeqQcRecs.size()>0){
            for (DataRecord record: sampleLevelSeqQcRecs){
                if (record.getValue("SequencerRunFolder", user) != null){
                    sequencerTypes.add(getSequencerType(record.getStringVal("SequencerRunFolder", user).split("_")[0]));
                }
            }
        }
        return StringUtils.join(", ", sequencerTypes);
   }

   private String getSequencerType(String machineName){
       if(HISEQ_2500_MACHINE_NAMES.contains(machineName.toUpperCase())){
           return "HiSeq 2500";
       }
       if(HISEQ_4000_MACHINE_NAMES.contains(machineName.toUpperCase())){
           return "HiSeq 4000";
       }
       if(MISEQ_MACHINE_NAMES.contains(machineName.toUpperCase())){
           return "MiSeq";
       }
       if(NOVASEQ_MACHINE_NAMES.contains(machineName.toUpperCase())){
           return "NovaSeq";
       }
       if(NEXTSEQ_MACHINE_NAMES.contains(machineName.toUpperCase())){
           return "NextSeq";
       }
       return "NA";
   }

   private JSONObject getConsentStatus(String consentType) throws MalformedURLException, JSONException {
       StringBuffer response = new StringBuffer();
       JSONObject cvrResponseData = new JSONObject() ;
       URL url = null;
       if (consentType.toLowerCase().equals("parta")){
           url = new URL("http://draco.mskcc.org:9890/get_12245_list_parta");
       }
       else {
           url = new URL("http://draco.mskcc.org:9890/get_12245_list_partc");
       }
       try {
           HttpURLConnection con = (HttpURLConnection) url.openConnection();
           con.setRequestMethod("GET");
           BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
           String inputLine;
           while ((inputLine = in.readLine())!= null) {
               response.append(inputLine);
           }
           in.close();
           con.disconnect();
           cvrResponseData = new JSONObject(response.toString());
           //log.info(cvrResponseData.getJSONObject("cases"));
       } catch (Exception e) {
           log.error(String.format("Error occured while querying consent '%s' end point.\n", consentType));
       }
       return cvrResponseData.getJSONObject("cases");
   }

    /**
     * Method to get last child sample of sample passed to the method. A Sample can have children across requests,
     * this method returns last  child from the same request as sample passed to the method.
     * @param sample
     * @return
     * @throws IoError
     * @throws RemoteException
     */
    private DataRecord getLastChildSample(DataRecord sample, String requestId) throws IoError, RemoteException, NotFound {
        List<DataRecord> childSamples = sample.getDescendantsOfType("Sample", user);
        List<DataRecord> childRecordsForSameRequest = new ArrayList<>();
        if(childSamples.size()>0){
            childRecordsForSameRequest   = childSamples.stream().filter(s -> {
                try {
                    return (requestId.toLowerCase().equals(s.getStringVal("RequestId", user).trim().toLowerCase())
                            || requestId.toLowerCase().contains(s.getStringVal("RequestId", user).trim().toLowerCase())
                            || s.getStringVal("RequestId", user).trim().toLowerCase().contains(requestId.toLowerCase()));
                } catch (NotFound | RemoteException notFound) {
                    notFound.printStackTrace();
                    return false;
                }
            }).collect(Collectors.toList());
            if (childRecordsForSameRequest.size()>0) {
                return childRecordsForSameRequest.stream().max(Comparator.comparing(DataRecord::getRecordId)).orElse(sample);
            }
        }
        return sample;
    }

    private String getLatestIGOStatus(DataRecord sample, String requestId) throws IoError, RemoteException, NotFound {
        return getLastChildSample(sample, requestId).getStringVal("ExemplarSampleStatus", user);
    }
}



//    private long getDateIGOComplete(DataRecord request) throws NotFound, RemoteException {
//        if (request.getValue("CompletedDate", user)!=null){
//            return request.getLongVal("CompletedDate", user);
//        }
//        return 0;
//    }
//    //Utility function
//    private static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
//        Map<Object, Boolean> map = new ConcurrentHashMap<>();
//        return t -> map.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
//    }
//
//    private String getCorrectedCMOSampleId(DataRecord sample) throws RemoteException, NotFound {
//        List<DataRecord> cmoInfoRecords = sample.getDescendantsOfType("SampleCMOInfoRecords", user);
//        if (cmoInfoRecords.size()>0){
//            if (cmoInfoRecords.get(0).getValue("CorrectedCMOID", user) !=null){
//                return cmoInfoRecords.get(0).getStringVal("CorrectedCMOID", user);
//            }
//        }
//        return "NA";
//    }
//
//
//    private String getTumorType(DataRecord sample) throws RemoteException, NotFound {
//        List<DataRecord> cmoInfoRecords = sample.getDescendantsOfType("SampleCMOInfoRecords", user);
//        if (cmoInfoRecords.size()>0){
//            if (cmoInfoRecords.get(0).getValue("TumorType", user) !=null){
//                cmoInfoRecords.get(0).getStringVal("TumorType", user);
//                return cmoInfoRecords.get(0).getStringVal("TumorType", user);
//            }
//            if (sample.getValue("TumorType", user) !=null){
//                return sample.getStringVal("TumorType", user);
//            }
//        }
//        return "NA";
//    }
//

//    private String getCMOSampleClass(DataRecord sample) throws RemoteException, NotFound {
//        List<DataRecord> cmoInfoRecords = sample.getDescendantsOfType("SampleCMOInfoRecords", user);
//
//        if (cmoInfoRecords.size()>0){
//            if (cmoInfoRecords.get(0).getValue("CMOSampleClass", user) !=null){
//                return cmoInfoRecords.get(0).getStringVal("CMOSampleClass", user);
//            }
//        }
//        if (sample.getValue("CMOSampleClass", user)!=null){
//            return sample.getStringVal("CMOSampleClass", user);
//        }
//        return "NA";
//    }
//
//
//    private String getTumorSite(DataRecord sample) throws RemoteException, NotFound {
//        List<DataRecord> cmoInfoRecords = sample.getDescendantsOfType("SampleCMOInfoRecords", user);
//        log.info(cmoInfoRecords.size());
//        if (cmoInfoRecords.size()>0){
//            if (cmoInfoRecords.get(0).getValue("TissueLocation", user) !=null){
//                log.info(cmoInfoRecords.get(0).getStringVal("TissueLocation", user));
//                return cmoInfoRecords.get(0).getStringVal("TissueLocation", user);
//            }
//            if (sample.getValue("TissueLocation", user) != null) {
//                log.info(sample.getStringVal("TissueLocation", user));
//                return sample.getStringVal("TissueLocation", user);
//            }
//        }
//        return "NA";
//    }

//
//    public String convertTimeToDateTime(long time){
//        if (time>0) {
//            Date date = new Date(time);
//            Format format = new SimpleDateFormat("yyyy MM dd HH:mm:ss");
//            return format.format(date);
//        }
//        return "";
//    }
//
//
//    private List<WESSampleData> createWESSampleDataForSample(DataRecord sample) throws RemoteException, NotFound, IoError {
//        List<DataRecord> sampleRequests = getRelatedRequests(sample);
//        List<WESSampleData> wesSamples = new ArrayList<>();
//        if (sampleRequests.size()>0){
//            for (DataRecord request: sampleRequests){
//                String sampleId = sample.getStringVal("SampleId", user);
//                String mrn = generateRandomMrn();
//                String otherSampleId = sample.getStringVal("OtherSampleId", user);
//                String correctedCMOId = getCorrectedCMOSampleId(sample);
//                String requestId = request.getStringVal("RequestId", user);
//                String accessionNumber = generateAccessionNumber();
//                String oncoTreeCode = generateOncoTreeCode();
//                String tumorType = getTumorType(sample);
//                String tumorSite = getTumorSite(sample);
//                String associatedClinicalTrial = "NA";
//                String pi = request.getStringVal("LaboratoryHead", user);
//                String sampleStatus = "In process at IGO";//will change as it is clear where it should come from.
//                String accessStatus = "Private or public";//will change as it is clear where it should come from.
//                String dataAvailabilityStatus = "Available/Not available";// //will change as it is clear where it should come from.
//                String dateCreated = convertTimeToDateTime((long)request.getValue("DateCreated", user));
//                String dateIgoReceived = request.getValue("ReceivedDate", user)!=null ? convertTimeToDateTime((long)request.getValue("ReceivedDate", user)):"";
//                String tissueLocation = getTumorSite(sample);//Need clarity between TumorSite and TissueLocation. Will change when clear.
//                String cmoSampleClass = getCMOSampleClass(sample);
//                String sampleStatusAtIGO = getLatestIGOStatus(sample, requestId);
//                String dateIGOComplete = convertTimeToDateTime(getDateIGOComplete(request));
//                long recordId = sample.getRecordId();
//                WESSampleData wesSample = new WESSampleData(sampleId, otherSampleId,correctedCMOId, mrn, requestId, accessionNumber, oncoTreeCode, tumorType, tumorSite, associatedClinicalTrial, pi, sampleStatus, accessStatus,
//                        dataAvailabilityStatus, dateCreated, dateIgoReceived, tissueLocation, cmoSampleClass, sampleStatusAtIGO, dateIGOComplete, recordId);
//                wesSamples.add(wesSample);
//            }
//        }
//        return wesSamples;
//    }