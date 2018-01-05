package org.mskcc.limsrest.limsapi.cmoinfo;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mskcc.domain.CorrectedCmoSampleView;
import org.mskcc.limsrest.limsapi.PatientSamplesRetriever;
import org.mskcc.limsrest.limsapi.cmoinfo.retriever.CmoSampleIdRetriever;
import org.mskcc.limsrest.limsapi.cmoinfo.retriever.CmoSampleIdRetrieverFactory;
import org.mskcc.util.CommonUtils;
import org.mskcc.util.notificator.Notificator;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.mskcc.domain.sample.SpecimenType.CELLLINE;

/**
 * SampleTypeCorrectedCmoSampleGenerator generates CMO Sample ID for both Cell line and non-Cell line samples.
 * In case of Cmo Sample Id generation error notification will be sent.
 */
public class SampleTypeCorrectedCmoSampleIdGenerator implements CorrectedCmoSampleIdGenerator {
    private final static Log LOGGER = LogFactory.getLog(SampleTypeCorrectedCmoSampleIdGenerator.class);

    private final CmoSampleIdRetrieverFactory cmoSampleIdRetrieverFactory;
    private final Multimap<String, CorrectedCmoSampleView> generatedSamples = HashMultimap.create();
    private final PatientSamplesRetriever patientSamplesRetriever;
    private final Notificator notificator;

    public SampleTypeCorrectedCmoSampleIdGenerator(CmoSampleIdRetrieverFactory cmoSampleIdRetrieverFactory,
                                                   PatientSamplesRetriever patientSamplesRetriever,
                                                   Notificator notificator) {
        this.cmoSampleIdRetrieverFactory = cmoSampleIdRetrieverFactory;
        this.patientSamplesRetriever = patientSamplesRetriever;
        this.notificator = notificator;
    }

    @Override
    public synchronized String generate(CorrectedCmoSampleView correctedCmoSampleView, String requestId,
                                        DataRecordManager dataRecordManager, User user) {
        try {
            String patientId = correctedCmoSampleView.getPatientId();
            CommonUtils.requireNonNullNorEmpty(patientId, String.format("Patient id is not set for sample: %s",
                    correctedCmoSampleView.getId()));

            List<CorrectedCmoSampleView> cmoSampleViews = patientSamplesRetriever.retrieve(patientId,
                    dataRecordManager, user);

            CmoSampleIdRetriever cmoSampleIdRetriever = cmoSampleIdRetrieverFactory.getCmoSampleIdRetriever
                    (correctedCmoSampleView);

            cmoSampleViews.addAll(generatedSamples.get(patientId));
            LOGGER.info(String.format("Added %d samples for patient %s generated during current run for cmo id " +
                    "generation: %s", generatedSamples.size(), patientId, generatedSamples.values()));

            List<CorrectedCmoSampleView> filteredViews = getFilteredCmoViews(correctedCmoSampleView, cmoSampleViews);
            String cmoSampleId = cmoSampleIdRetriever.retrieve(correctedCmoSampleView, filteredViews, requestId);

            correctedCmoSampleView.setCorrectedCmoId(cmoSampleId);
            generatedSamples.put(patientId, correctedCmoSampleView);

            return cmoSampleId;
        } catch (Exception e) {
            notifyAboutCorrectedCmoIdFailure(correctedCmoSampleView, requestId, e);
            throw new RuntimeException(e);
        }
    }

    private List<CorrectedCmoSampleView> getFilteredCmoViews(CorrectedCmoSampleView correctedCmoSampleView,
                                                             List<CorrectedCmoSampleView> cmoSampleViews) {
        List<CorrectedCmoSampleView> cmoSampleViewWithoutCurrent = removeCurrent(cmoSampleViews,
                correctedCmoSampleView);
        return removeCellLineViews(cmoSampleViewWithoutCurrent);
    }

    private List<CorrectedCmoSampleView> removeCurrent(List<CorrectedCmoSampleView> cmoSampleViews,
                                                       CorrectedCmoSampleView correctedCmoSampleView) {
        List<CorrectedCmoSampleView> filteredCmoViews = cmoSampleViews.stream()
                .filter(s -> !Objects.equals(s.getId(), correctedCmoSampleView.getId()))
                .collect(Collectors.toList());
        return filteredCmoViews;
    }

    private List<CorrectedCmoSampleView> removeCellLineViews(List<CorrectedCmoSampleView> cmoSampleViews) {
        List<CorrectedCmoSampleView> cellLines = cmoSampleViews.stream()
                .filter(v -> v.getSpecimenType() == CELLLINE)
                .collect(Collectors.toList());

        if (cellLines.size() > 0)
            LOGGER.info(String.format("Omitting cell lines samples: %s", cellLines));

        List<CorrectedCmoSampleView> nonCellLines = cmoSampleViews.stream()
                .filter(s -> !cellLines.contains(s))
                .collect(Collectors.toList());

        return nonCellLines;
    }

    private void notifyAboutCorrectedCmoIdFailure(CorrectedCmoSampleView correctedCmoSampleView, String requestId,
                                                  Exception exception) {
        String message = String.format(":-1: Corrected cmo id autogeneration failed for sample: *%s (%s)* :-1: \n " +
                        "Cause: %s",
                correctedCmoSampleView.getSampleId(), correctedCmoSampleView.getId(), getStackTraceAsString(exception));

        try {
            notificator.notifyMessage(requestId, message);
        } catch (Exception e) {
            LOGGER.warn(String.format("Sending notification about failure to autogenerate corrected cmo id failed for" +
                    " sample: %s", correctedCmoSampleView.getId()));
        }
    }

    private String getStackTraceAsString(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
