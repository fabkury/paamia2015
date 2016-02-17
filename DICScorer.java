
package hedicim;

import java.util.*;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author kuryfs
 */
public class DICScorer {
    static final float icd9s_associated_with_DIC[] = {
        286.6f, // DIC itself,       
        570f, // Severe hepatic failure -- Acute and subacute necrosis of liver.			
        989.5f, // Toxic effect of venom [1 record in whole MIMIC-II]
        289.81f, // Primary hypercoagulable state (289.81)
        286.53f, // Antiphospholipid antibody with hemorrhagic disorder (286.53)
        785.52f, // SEPTIC SHOCK -- 1064 HADM_IDs in MIMIC-II
    };
    
    // In the intervals below, beginnings are inclusive, endings are exclusive.
    static final float icd9_intervals_associated_with_DIC[][] = {
        {995.9f, 996f}, // Sepsis: 2013 ICD-9-CM Diagnosis Code 995.91
        {577f, 577.2f}, // Acute and chronic pancreatitis:  ICD-9-CM 577.0 and 577.1
        {140f, 210f}, // (Malignant) Neoplasms of all kinds
        {999.8f, 999.9f}, // Transfusion reactions
        {441f, 442f}, // Vascular abnormalities, eg.: Abdominal aneurysm, ruptured -- 441.3
        {996.8f, 996.9f}, // Transplant rejection, eg.: Complications of transplanted organ: 2012 ICD-9-CM Diagnosis Code 996.8
        {992f, 993f}, // Heat stroke and hyperthermia, eg.: Heat stroke and sunstroke: 2014 ICD-9-CM Diagnosis Code 992.0
        {038f, 039f}, // SEPTICEMIA -- 3309 HADM_IDs in MIMIC-II.
        {800f, 802f}, // Some trauma patients
        {803f, 805f}, // Some trauma patients
        {808f, 809f}, // Some trauma patients
        {925f, 930f} // Some trauma patients
    };

    int[] getScoresToReport() {
        return null;
    }
    
    int getDiagnosticScore() {
        return getDiagnosticScore(null, null);
    }
    
    int getDiagnosticScore(boolean[] flags, Patient patient) {
        return 0;
    }
    
    int getDiagnosticScore(Patient patient) {
        return getDiagnosticScore(calculatePatientFlags(patient), patient);
    }
    
    boolean[] calculatePatientFlags(Patient patient) {
        /* Calculates values that pertain to the whole patient, not to the lab values or charted data.
        * In other words, this function exists just to save processing time.
        */
        return null;
    }
    
    static boolean hasConditionAssociatedWithDIC(Patient patient) {
        if(Const.bypassConditionAssociatedWithDIC)
            return true;
        for(float icd9 : patient.icd9s) {
            for(float icd9_associated_with_DIC : icd9s_associated_with_DIC)
                if(icd9 == icd9_associated_with_DIC)
                    return true;
            for(float[] interval : icd9_intervals_associated_with_DIC)
                if(icd9 >= interval[0] && icd9 < interval[1])
                    // Notice that interval beginnings are inclusive while ends are exclusive.
                    return true;
        }

        return false;
    }
        
    Map<Integer, List<DICPeriod>> calculateDICPeriods(Patient patient) {
        Map<Integer, List<DICPeriod>> DIC_periods_map = new HashMap<>();                
        List<DICScore> dic_scores = calculateDICEvents(patient);
        for(int score_to_report : getScoresToReport())
            for(int i=0; i < dic_scores.size(); i++)
                if(dic_scores.get(i).has_dic && dic_scores.get(i).score >= score_to_report) {
                    DICPeriod dic_period = new DICPeriod();
                    dic_period.timestamps[0] = dic_scores.get(i).date.getTime();
                    // Fast-forward to the last score that still meets the criteria.
                    for(;i < dic_scores.size()-1; ++i) {
                        dic_period.dic_scores.add(dic_scores.get(i));
                        if(!dic_scores.get(i).has_dic || dic_scores.get(i).score < score_to_report) {
                            dic_period.dic_scores.remove(dic_period.dic_scores.size()-1);
                            i--; // We are already out! Get back to previous.
                            break;
                        }
                    }
                    dic_period.timestamps[1] = dic_scores.get(i).date.getTime();
                    
                    if(dic_period.timestamps[1] < dic_period.timestamps[0])
                        // This is supposed to NEVER happen, because the LABEVENTS are sorted by CHARTTIME.
                        // But, just in case something happens in the future, I've left this 'if' here.
                        //TIME_errors++;
                        System.out.println("Big exception in calculateDICPeriods() for hedicim_id="+patient.origin_id+" at i=" + i + ".");                                            

                    if(dic_period.timestamps[1] == dic_period.timestamps[0])
                        // Give symbolic duration to events which start and end at the same event.                                            
                        dic_period.timestamps[1] = dic_period.timestamps[0] + (30*60*1000); // Half an hour.

                    if(patient.ts_first_dic_event == 0 || patient.ts_first_dic_event > dic_period.timestamps[0])
                        patient.ts_first_dic_event = dic_period.timestamps[0];
                    
                    if(patient.ts_first_medication_is_relevant_event == 0 || patient.ts_first_medication_is_relevant_event > dic_period.timestamps[0])
                        patient.ts_first_medication_is_relevant_event = dic_period.timestamps[0];
                    if(patient.ts_last_medication_is_relevant_event < dic_period.timestamps[1])
                        patient.ts_last_medication_is_relevant_event = dic_period.timestamps[1];
                        
                    if(DIC_periods_map.containsKey(score_to_report))
                        ((List<DICPeriod>)DIC_periods_map.get(score_to_report)).add(dic_period);
                    else {
                        List<DICPeriod> dic_periods = new ArrayList<>();
                        dic_periods.add(dic_period);
                        DIC_periods_map.put(score_to_report, dic_periods);
                    }
                }
        
        return DIC_periods_map;
    }

    DICScore calculateDICScore(boolean[] flags, Map<ITEMID, EVENT> events_map) {
        return null;
    }
    
    boolean thisDICSSCanBeUsed(boolean[] flags, Patient patient) {
        return false;
    }
    
    DICSS getDICSS() {
        return null;
    }
    
    ITEMID getITEMID() {
        return null;
    }
    
    DICScore findDICInLabWindow(Patient patient, boolean[] patient_flags, int i) {
        // Find all previous lab events inside the time window
        // Notice this code still makes use of an old concept, not deprecated, of getting
        // the lab result representing the worst-case scenario instead of the latest one.        
        DICScore dic_score = calculateDICScore(patient_flags, patient.mapEventsBeforePointInList(i));            
        // Score calculated!        
        dic_score.date = patient.events.get(i).CHARTTIME;
        if(dic_score.score >= getDiagnosticScore(patient_flags, patient)) {
            dic_score.has_dic = true;
            patient.has_dic = true;
            /*
            if(!patient.has_1_day_dic.containsKey(getDICSS())) {
                patient.has_1_day_dic.put(getDICSS(), dic_score);
                if(getDICSS()==DICSS.JAAM)
                    patient.setStatCell("jaam score at day 1", Double.toString(dic_score.score));
                else if(getDICSS()==DICSS.ISTH)
                    patient.setStatCell("isth score at day 1", Double.toString(dic_score.score));
            } else if(!patient.has_4_day_dic.containsKey(getDICSS()) && patient.has_1_day_dic.containsKey(getDICSS())
                // (double-check has_1_day_dic to keep safe from the "if-else chaining")
                && dic_score.date.getTime() - patient.has_1_day_dic.get(getDICSS()).date.getTime() >=
                Const.STANDARD_DISTANCE_WINDOW*3
                && dic_score.date.getTime() - patient.has_1_day_dic.get(getDICSS()).date.getTime() < 
                Const.STANDARD_DISTANCE_WINDOW*4) {
                patient.has_4_day_dic.put(getDICSS(), dic_score);
                if(DICSS.JAAM.equals(getDICSS()))
                    patient.setStatCell("jaam score at day 4", Double.toString(dic_score.score));
                else if(DICSS.ISTH.equals(getDICSS()))
                    patient.setStatCell("isth score at day 4", Double.toString(dic_score.score));
                patient.sofa_4_day = patient.findEventByTimeWindowCenter(
                        dic_score.date.getTime()-Const.STANDARD_DISTANCE_WINDOW,
                        dic_score.date.getTime()+Const.STANDARD_DISTANCE_WINDOW, ITEMID.Overall_SOFA_Score);
            }*/
        }
        else
            dic_score.has_dic = false;
        return dic_score;
    }
    
    DICScore calculateDICAtPointInTime(Patient patient, long timestamp) {
        DICScore dic_score = new DICScore();
        boolean[] patient_flags = calculatePatientFlags(patient);
        if(!thisDICSSCanBeUsed(patient_flags, patient))
            return dic_score;
        // Iterate through the lab tests, get the worst-case lab test of each type in case of duplicates
        int i=0;
        for(i=0; i < patient.events.size(); i++)
            // Fast-forward to past the timestamp
            if(patient.events.get(i).CHARTTIME.getTime() > timestamp) {
                i--;
                break;
            }        
        if(timestamp-patient.events.get(i).CHARTTIME.getTime() > Const.LAB_TIME_WINDOW)
            // TO DO: Improve this. There is a leak of time exceeding the LAB_TIME_WINDOW. findDICInLabWindow() should
            // use timestamps instead of i values.
            return dic_score;
        
        return findDICInLabWindow(patient, patient_flags, patient.fastForwardToSameTimestamp(i));
    }
    
    List<DICScore> calculateDICEvents(Patient patient) {
        // NOTICE: The constants remove the magic numbers from this function, but not the magic
        // operators. The calculator defined by the ISTH requires correct use of >, >=, < and <=.
        List<DICScore> DIC_scores = new ArrayList<>();
        
        boolean[] patient_flags = calculatePatientFlags(patient);
        if(!thisDICSSCanBeUsed(patient_flags, patient))
            // No condition associated with DIC. The ISTH calculator can't be used. Return an empty list.
            return DIC_scores;
        
        // Iterate through the lab tests, get the worst-case lab test of each type in case of duplicates
        for(int i=0; i < patient.events.size(); i++) {
            // Fast-foward to all events charted at the same time. This often is the case.
            for(int f=i+1; f < patient.events.size(); f++) {
                if(patient.events.get(f).CHARTTIME.getTime() < patient.events.get(i).CHARTTIME.getTime()) {
                    // This never happened, because the LABEVENTS are sorted by CHARTTIME.
                    // But, just in case something happens in the future, I've left this 'if' here.
                    System.out.println("Big exception in calculateDICEvents() at origin_id=" + 
                        patient.origin_id + ", f=" + f + ", i=" + i + ".");
                }
                    
                if(patient.events.get(f).CHARTTIME.getTime() != patient.events.get(i).CHARTTIME.getTime())
                    break;
                else
                    i=f;
            }
            
            DIC_scores.add(findDICInLabWindow(patient, patient_flags, i));
        }
        
        for(DICScore dic_score : DIC_scores)
            if(dic_score.score > 0d)
                patient.events.add(new EVENT(ITEMID.DICSS_to_ITEMID.get(getDICSS()).value, dic_score.date,
                    dic_score.score.toString(), (float)dic_score.score.intValue(), null, null));
        patient.sortEvents();

        return DIC_scores;
   }
}
