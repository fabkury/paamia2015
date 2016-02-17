
package hedicim;

import static java.lang.Integer.max;
import java.util.*;


/**
 *
 * @author kuryfs
 */
public class KSTHDICScorer extends DICScorer {    
    static final Float FDP_L1 = 20f; // Threshold for fibrin degradation products ratio to be considered elevated
    static final Float D_DIMER_L1 = 0.4f * 1000; // Threshold for D-Dimer to be considered moderately elevated, in nanograms/milliliter.
    static final Float PT_UPPER_BASELINE = 13.0f; // Threshold for PT to be considered elevated
    static final Float PT_L1 = PT_UPPER_BASELINE+3f; // Threshold for PT to be considered elevated
    static final Float APTT_UPPER_BASELINE = 35.0f; // Threshold for PT to be considered elevated
    static final Float APTT_L1 = APTT_UPPER_BASELINE+5f; // Threshold for aPTT to be considered elevated.
    static final Float FIBRINOGEN_L1 = 1.5f * 100; // Threshold for fibrinogen to be considered normal, 1.5 g/L, in mg/dL
    static final Float PLATELET_L1 = 100f; // Threshold for platelet count to be considered very low
    
    static final int FLAG_HAS_CONDITION_PREDISPOSING_TO_DIC = 0;
    
    @Override
    DICSS getDICSS() {
        return DICSS.KSTH;
    }
    
    @Override
    ITEMID getITEMID() {
        return ITEMID.DIC_SCORE_KSTH;
    }
    
    @Override
    int[] getScoresToReport() {
        return new int[]{2, 3, 4};
    }
    
    @Override
    int getDiagnosticScore(boolean[] flags, Patient hospitalization) {
        return 3;
    }
    
    @Override
    boolean[] calculatePatientFlags(Patient hospitalization) {
        boolean[] hospitalization_flags = new boolean [1];
        hospitalization_flags[FLAG_HAS_CONDITION_PREDISPOSING_TO_DIC] = hasConditionAssociatedWithDIC(hospitalization);
        return hospitalization_flags;
    }
    
    @Override
    boolean thisDICSSCanBeUsed(boolean[] flags, Patient hospitalization) {
        return flags[FLAG_HAS_CONDITION_PREDISPOSING_TO_DIC];
    }

    @Override
    DICScore calculateDICScore(boolean[] flags, Map<ITEMID, EVENT> events_map) {
        DICScore dic_score = new DICScore();
        
        EVENT platelet_count = events_map.get(ITEMID.PLATELET);
        EVENT d_dimer = events_map.get(ITEMID.D_DIMER);
        EVENT fdp = events_map.get(ITEMID.FDP);
        EVENT fibrinogen = events_map.get(ITEMID.FIBRINOGEN);
        EVENT pt = events_map.get(ITEMID.PT);
        EVENT aptt = events_map.get(ITEMID.APTT);
        
        // Calculate the ISTH score and report it in case of overt DIC
        // Notice that we do need to check whether the results exist or not -- this is done
        // by comparing the ITEMID to the constants they are supposed to hold.

        // Because the PT and PT Ratio interpretations are interlinked, and our intent is to
        // calculate the score for the worst case scenario (that is, the combination of results
        // that lead to the worst diagnosis), we need to separate their contributions to the
        // ISTH score and then use the worst one.
        Integer PT_contribution_to_score = 0;
        Integer APTT_contribution_to_score = 0;
        if(pt != null)
            if(pt.VALUENUM > PT_L1)
                PT_contribution_to_score = 1;

        if(aptt != null)
            if(aptt.VALUENUM > APTT_L1)
                APTT_contribution_to_score = 1;

        if(PT_contribution_to_score > 0 || APTT_contribution_to_score > 0) {
            if(PT_contribution_to_score > APTT_contribution_to_score)
                dic_score.addScore(ITEMID.PT, PT_contribution_to_score);
            else
                dic_score.addScore(ITEMID.APTT, APTT_contribution_to_score);
        }

        if(fibrinogen != null)
            if(fibrinogen.VALUENUM < FIBRINOGEN_L1)
                dic_score.addScore(ITEMID.FIBRINOGEN, 1);

        // Because the D-Dimer and Fibrin Degradation Products interpretations are interlinked,
        // and our intent is to calculate the score for the worst case scenario (that is, the
        // combination of results that leads to the worst diagnosis), we need to separate their
        // contributions to the ISTH score and then use the worst one.
        Integer D_dimer_contribution_to_score = 0;
        Integer FDP_contribution_to_score = 0;
        if(d_dimer != null)
            if(d_dimer.VALUENUM >= D_DIMER_L1)
                D_dimer_contribution_to_score = 1;

        if(fdp != null && !fdp.VALUE.isEmpty())
            if(fdp.VALUENUM >= FDP_L1)
                // All other values represent higher levels than 10-40 -- I checked that manually.
                FDP_contribution_to_score = 1;

        dic_score.addScore(ITEMID.HEDICIM_FIBRIN_RELATED_MARKER, max(D_dimer_contribution_to_score, FDP_contribution_to_score));

        if(platelet_count != null)
            if(platelet_count.VALUENUM < PLATELET_L1)
                dic_score.addScore(ITEMID.PLATELET, 1);
        
        return dic_score;
    }
}

