package org.mskcc.limsrest.service.cmoinfo;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mskcc.domain.sample.CorrectedCmoSampleView;
import org.mskcc.limsrest.service.PatientSamplesWithCmoInfoRetriever;
import org.mskcc.limsrest.service.cmoinfo.cellline.CellLineCmoSampleIdFormatter;
import org.mskcc.limsrest.service.cmoinfo.cellline.CellLineCmoSampleIdResolver;
import org.mskcc.limsrest.service.cmoinfo.converter.FormatAwareCorrectedCmoIdConverterFactory;
import org.mskcc.limsrest.service.cmoinfo.converter.SampleToCorrectedCmoIdConverter;
import org.mskcc.limsrest.service.cmoinfo.cspace.CspaceSampleTypeAbbreviationRetriever;
import org.mskcc.limsrest.service.cmoinfo.cspace.StringCmoIdToCmoIdConverter;
import org.mskcc.limsrest.service.cmoinfo.patientsample.PatientAwareCmoSampleId;
import org.mskcc.limsrest.service.cmoinfo.patientsample.PatientCmoSampleIdFormatter;
import org.mskcc.limsrest.service.cmoinfo.patientsample.PatientCmoSampleIdResolver;
import org.mskcc.limsrest.service.cmoinfo.retriever.CmoSampleIdRetriever;
import org.mskcc.limsrest.service.cmoinfo.retriever.CmoSampleIdRetrieverFactory;
import org.mskcc.limsrest.service.cmoinfo.retriever.FormattedCmoSampleIdRetriever;
import org.mskcc.limsrest.service.cmoinfo.retriever.IncrementalSampleCounterRetriever;
import org.mskcc.limsrest.service.converter.SampleRecordToSampleConverter;
import org.mskcc.util.CommonUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.domain.sample.SpecimenType.CELLLINE;

/**
 * SampleTypeCorrectedCmoSampleGenerator generates CMO Sample ID for both Cell line and non-Cell line samples.
 * In case of Cmo Sample Id generation error notification will be sent.
 */
public class SampleTypeCorrectedCmoSampleIdGenerator implements CorrectedCmoSampleIdGenerator {
    private final static Log LOGGER = LogFactory.getLog(SampleTypeCorrectedCmoSampleIdGenerator.class);

    // TODO refactor or re-write with simpler implementation
    private final CmoSampleIdRetrieverFactory cmoSampleIdRetrieverFactory =
            new CmoSampleIdRetrieverFactory(
                    new FormattedCmoSampleIdRetriever(new PatientCmoSampleIdResolver(new IncrementalSampleCounterRetriever(new FormatAwareCorrectedCmoIdConverterFactory(new CspaceSampleTypeAbbreviationRetriever())),
                            new CspaceSampleTypeAbbreviationRetriever()), new PatientCmoSampleIdFormatter()),
                    new FormattedCmoSampleIdRetriever(new CellLineCmoSampleIdResolver(), new CellLineCmoSampleIdFormatter()));
    protected PatientSamplesWithCmoInfoRetriever patientSamplesRetriever = new PatientSamplesWithCmoInfoRetriever(new SampleToCorrectedCmoIdConverter(), new SampleRecordToSampleConverter());
    private Map<String, List<CorrectedCmoSampleView>> generatedSamples = new HashMap<>();

    public SampleTypeCorrectedCmoSampleIdGenerator() {
    }

    @Override
    public String generate(CorrectedCmoSampleView correctedCmoSampleView, String requestId, DataRecordManager dataRecordManager, User user) {
        LOGGER.info(String.format("Generating cmo id for view: %s", correctedCmoSampleView));

        try {
            synchronized (SampleTypeCorrectedCmoSampleIdGenerator.class) {
                String patientId = correctedCmoSampleView.getPatientId();
                CommonUtils.requireNonNullNorEmpty(patientId, String.format("Patient id is not set for sample: %s", correctedCmoSampleView.getId()));

                // get all samples for that patient in the LIMS
                List<CorrectedCmoSampleView> cmoSampleViews = patientSamplesRetriever.retrieve(patientId, dataRecordManager, user);
                // add to that list all samples where patient ID was just generated but not yet saved to LIMS DB
                List<CorrectedCmoSampleView> generated = generatedSamples.get(patientId);
                if (generated != null)
                    cmoSampleViews.addAll(generated);
                LOGGER.info(String.format("Added %d samples for patient %s generated during current run for cmo id " +
                        "generation: %s", generatedSamples.size(), patientId, generatedSamples.values()));
                List<CorrectedCmoSampleView> filteredViews = getFilteredCmoViews(correctedCmoSampleView, cmoSampleViews);

                CmoSampleIdRetriever idRetriever = cmoSampleIdRetrieverFactory.getCmoSampleIdRetriever(correctedCmoSampleView);
                String cmoSampleId = idRetriever.retrieve(correctedCmoSampleView, filteredViews, requestId);

                if (shouldOverrideCmoId(correctedCmoSampleView, cmoSampleId))
                    correctedCmoSampleView.setCorrectedCmoId(cmoSampleId);

                if (generatedSamples.containsKey(patientId)) {
                    generatedSamples.get(patientId).add(correctedCmoSampleView);
                } else {
                    generatedSamples.put(patientId, new ArrayList<>(Arrays.asList(correctedCmoSampleView)));
                }
            }
            return correctedCmoSampleView.getCorrectedCmoId();
        } catch (Exception e) {
            notifyAboutCorrectedCmoIdFailure(correctedCmoSampleView, requestId, e);
            throw new RuntimeException(e);
        }
    }

    protected static boolean shouldOverrideCmoId(CorrectedCmoSampleView correctedCmoSampleView, String cmoSampleId) {
        return StringUtils.isEmpty(correctedCmoSampleView.getCorrectedCmoId()) ||
                !isSame(correctedCmoSampleView.getCorrectedCmoId(), cmoSampleId);
    }

    protected static boolean isSame(String current, String potential) {
        try {
            StringCmoIdToCmoIdConverter stringCmoIdToCmoIdConverter = new StringCmoIdToCmoIdConverter();

            PatientAwareCmoSampleId currentId = stringCmoIdToCmoIdConverter.convert(current);
            PatientAwareCmoSampleId potentialId = stringCmoIdToCmoIdConverter.convert(potential);

            if (!Objects.equals(currentId.getNucleicAcid(), potentialId.getNucleicAcid()))
                return false;
            if (!Objects.equals(currentId.getPatientId(), potentialId.getPatientId()))
                return false;
            if (!Objects.equals(currentId.getSampleTypeAbbr(), potentialId.getSampleTypeAbbr()))
                return false;

            LOGGER.info(String.format("Current cmo sample id %s and potential new one %s are the same. The only " +
                    "difference is the counter thus leaving current value", current, potential));

            return true;
        } catch (Exception e) {
            LOGGER.warn(String.format("Cannot determine if current: %s and new: %s cmo sample id are the same thus " +
                    "forcing overriding", current, potential), e);
            return false;
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
        String message =
                String.format(":-1: Corrected cmo id autogeneration failed for sample: *%s (%s)* :-1: \n Cause: %s",
                correctedCmoSampleView.getSampleId(), correctedCmoSampleView.getId(), exception.getMessage());

        try {
            //notificator.notifyMessage(requestId, message);
        } catch (Exception e) {
            LOGGER.warn(String.format("Sending notification about failure to autogenerate corrected cmo id failed for" +
                    " sample: %s", correctedCmoSampleView.getId()));
        }
    }
}
