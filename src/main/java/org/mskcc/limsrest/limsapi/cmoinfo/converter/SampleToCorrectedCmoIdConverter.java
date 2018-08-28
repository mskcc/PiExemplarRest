package org.mskcc.limsrest.limsapi.cmoinfo.converter;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mskcc.domain.sample.*;
import org.mskcc.limsrest.limsapi.LimsException;
import org.mskcc.limsrest.limsapi.PatientSamplesWithCmoInfoRetriever;
import org.mskcc.limsrest.util.Utils;

public class SampleToCorrectedCmoIdConverter implements CorrectedCmoIdConverter<Sample> {
    private final static Log LOGGER = LogFactory.getLog(PatientSamplesWithCmoInfoRetriever.class);

    @Override
    public CorrectedCmoSampleView convert(Sample sample) throws LimsException {
        LOGGER.debug(String.format("Converting sample %s to Corrected Cmo Sample View", sample));

        try {
            CorrectedCmoSampleView correctedCmoSampleView = new CorrectedCmoSampleView(sample.getIgoId());
            correctedCmoSampleView.setCorrectedCmoId(sample.getCorrectedCmoSampleId());
            correctedCmoSampleView.setSampleType(SampleType.fromString(sample.getExemplarSampleType()));

            correctedCmoSampleView.setSampleId(sample.getCmoSampleInfo().getUserSampleID());

            Utils.getOptionalNucleicAcid(sample.getNAtoExtract(), sample.getIgoId()).ifPresent
                    (correctedCmoSampleView::setNucleidAcid);

            correctedCmoSampleView.setPatientId(sample.getCmoSampleInfo().getCmoPatientId());
            correctedCmoSampleView.setRequestId(sample.getRequestId());
            correctedCmoSampleView.setRecipe(sample.getRecipe());

            if (!StringUtils.isEmpty(sample.getCorrectedSampleClass()))
                correctedCmoSampleView.setSampleClass(SampleClass.fromValue(sample.getCorrectedSampleClass()));

            if (!StringUtils.isEmpty(sample.getCorrectedCmoSampleOrigin()))
                correctedCmoSampleView.setSampleOrigin(SampleOrigin.fromValue(sample.getCorrectedCmoSampleOrigin()));

            if (!StringUtils.isEmpty(sample.getCorrectedSpecimenType()))
                correctedCmoSampleView.setSpecimenType(SpecimenType.fromValue(sample.getCorrectedSpecimenType()));

            if (!StringUtils.isEmpty(sample.getCorrectedCmoSampleId()))
                correctedCmoSampleView.setCorrectedCmoId(sample.getCorrectedCmoSampleId());

            LOGGER.debug(String.format("Converted sample %s to Corrected Cmo Sample View: %s", sample,
                    correctedCmoSampleView));

            return correctedCmoSampleView;
        } catch (Exception e) {
            throw new RuntimeException(String.format("Error while retrieving information for sample: %s. Couse: %s",
                    sample.getIgoId(), e.getMessage()));
        }
    }
}
