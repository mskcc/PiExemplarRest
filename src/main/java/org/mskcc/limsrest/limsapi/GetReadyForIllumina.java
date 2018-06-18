package org.mskcc.limsrest.limsapi;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.NotFound;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mskcc.limsrest.limsapi.search.NearestAncestorSearcher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.rmi.RemoteException;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A queued task that takes shows all samples that need planned for illumina runs 
 * 
 * @author Aaron Gabow
 * 
 */
@Service
public class GetReadyForIllumina extends LimsTask 
{
   private Log log = LogFactory.getLog(GetReadyForIllumina.class);
   private HashMap<String, List<String>> request2OutstandingSamples; 

  public void init(){
    request2OutstandingSamples  = new HashMap<>();
  }

 //execute the velox call
@PreAuthorize("hasRole('READ')")
@Override
 public Object execute(VeloxConnection conn) {
    List<RunSummary> results = new LinkedList<>();
    try {
        List<DataRecord> samplesToPool = dataRecordManager.queryDataRecords("Sample", "ExemplarSampleStatus = 'Ready for - Pooling of Sample Libraries for Sequencing'", user);
        for(DataRecord sample : samplesToPool){
            String sampleId = sample.getStringVal("SampleId", user);
            if(sampleId.startsWith("Pool-")){
                Deque<DataRecord> queue = new LinkedList<>();
                Set<DataRecord> visited = new HashSet<>();
                double poolConcentration = 0.0;
                try{
                    poolConcentration = sample.getDoubleVal("Concentration", user);
                } catch (NullPointerException npe){}
                double poolVolume = 0.0;
                try{
                    poolVolume = sample.getDoubleVal("Volume", user);
                }catch (NullPointerException npe){}
                String status = sample.getStringVal("ExemplarSampleStatus", user);
                queue.add(sample);
                while(!queue.isEmpty()){
                    DataRecord current = queue.removeFirst();
                    String currentSampleId = current.getStringVal("SampleId", user);
                    if(currentSampleId.startsWith("Pool-")){
                        List<DataRecord> parents = current.getParentsOfType("Sample", user);
                        for(DataRecord parent : parents) {
                            if (!visited.contains(parent)){
                                queue.addLast(parent);
                            }
                        }
                    } else{
                        RunSummary runSummary = annotateUnpooledSample(current);
                        runSummary.setPool(sampleId);
                        runSummary.setConcentration(poolConcentration);
                        runSummary.setVolume(Double.toString(poolVolume));
                        runSummary.setStatus(status);
                        results.add(runSummary);
                    }
                    visited.add(current);
                }

            } else{
                results.add(annotateUnpooledSample(sample));
            }
        }

    } catch (Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        log.info(e.getMessage());
        log.info(sw.toString());

        //rs = RequestSummary.errorMessage(e.getMessage());
    }

    return results;

}

private Long getCreateDate(DataRecord record){
    try{
        return record.getDateVal("DateCreated", user);
    } catch (NotFound | RemoteException e){
        log.info("Failed to find a date created for record: " + record.toString());
    }
    return  0L;
}

    public RunSummary annotateUnpooledSample(DataRecord sample) {
        RunSummary summary = new RunSummary("DEFAULT", "DEFAULT");
        try {
            Map<String, Object> baseFields = sample.getFields(user);
            summary = new RunSummary("", (String) baseFields.get("SampleId"));
            summary.setOtherSampleId((String) baseFields.getOrDefault("OtherSampleId", ""));
            summary.setRequestId((String) baseFields.getOrDefault("RequestId", ""));
            summary.setTubeBarcode((String) baseFields.getOrDefault("MicronicTubeBarcode", ""));
            summary.setStatus((String) baseFields.getOrDefault("ExemplarSampleStatus", ""));
            summary.setTumor((String) baseFields.getOrDefault("TumorOrNormal", ""));
            summary.setWellPos(baseFields.getOrDefault("ColPosition", "") + (String) baseFields.getOrDefault("RowPosition", ""));
            summary.setConcentrationUnits((String) baseFields.getOrDefault("ConcentrationUnits", ""));
            summary.setAltConcentration((Double) baseFields.getOrDefault("Concentration", Double.valueOf(0.0)));
            summary.setVolume(Double.toString((Double) baseFields.getOrDefault("Volume", Double.valueOf(0.0))));
            summary.setPlateId((String) baseFields.getOrDefault("RelatedRecord23", ""));


            List<DataRecord> ancestorSamples = sample.getAncestorsOfType("Sample", user);
            ancestorSamples.add(0, sample);
            List<Long> ancestorSamplesCreateDate = new LinkedList<>();
            ancestorSamplesCreateDate = ancestorSamples.stream().
                    map(this::getCreateDate).
                    collect(Collectors.toList());
            List<List<Map<String, Object>>> ancestorBarcodes = dataRecordManager.getFieldsForChildrenOfType(ancestorSamples,"IndexBarcode", user);
            List<List<Map<String, Object>>> ancestorPlanningProtocols = dataRecordManager.getFieldsForChildrenOfType(ancestorSamples,"BatchPlanningProtocol", user);
            List<List<Map<String, Object>>> ancestorSeqRequirements = dataRecordManager.getFieldsForChildrenOfType(ancestorSamples,"SeqRequirement", user);

            Map<String, Map<String, Object>> nearestAncestorFields =
                    NearestAncestorSearcher.findMostRecentAncestorFields(ancestorSamplesCreateDate,
                            ancestorBarcodes, ancestorPlanningProtocols, ancestorSeqRequirements);
            Map<String, Object> barcodeFields = nearestAncestorFields.get("BarcodeFields");
            Map<String, Object> planFields = nearestAncestorFields.get("PlanFields");
            Map<String, Object> reqFields = nearestAncestorFields.get("ReqFields");

            summary.setBarcodeId((String) barcodeFields.getOrDefault("IndexId", ""));
            summary.setBarcodeSeq((String) barcodeFields.getOrDefault("IndexTag", ""));
            summary.setSequencer((String) planFields.getOrDefault("RunPlan", ""));
            summary.setBatch((String) planFields.getOrDefault("WeekPlan", ""));
            summary.setRunType((String) reqFields.getOrDefault("SequencingRunType", ""));
            Object reads = reqFields.getOrDefault("RequestedReads", "");
            if(reads instanceof String){
                summary.setReadNum((String) reads);
            } else if (reads instanceof Double){
                summary.setReadNum(((Double) reads).toString());
            } else{
                summary.setReadNum("");
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            log.info(e.getMessage());
            log.info(sw.toString());
        }
        return summary;
     }
}