/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package hedicim;

import java.util.Map;

/**
 *
 * @author kuryfs
 */
public class SIRSScore extends ExplainedScore {
    static EVENT pickMostRecentTemperatureAndConvertToCelsius(Map<ITEMID, EVENT> events_map) {
        EVENT labe = null;
        for(ITEMID itemid : ITEMID.temperatures())
            if(events_map.containsKey(itemid))
                if(labe == null)
                    labe = events_map.get(itemid);
                else
                    if(events_map.get(itemid).CHARTTIME.getTime() > labe.CHARTTIME.getTime())
                        labe = events_map.get(itemid);
        
        if(labe != null) {
            for(ITEMID itemid : ITEMID.temperatures_farenheit()) // Convert Farenheit to Celsius
                if(labe.ITEMID == itemid.value) {
                    labe.VALUENUM = ((labe.VALUENUM-32f)*5f)/9f;
                    labe.VALUE = labe.VALUENUM.toString();
                    break;
                }
            labe.ITEMID = ITEMID.HEDICIM_TEMPERATURE_C.value; // Convert to our reference ITEMID.
        }
        
        return labe;
    }
    
    static EVENT pickMostRecentFromGroup(Map<ITEMID, EVENT> events_map, ITEMID[] group, ITEMID reference) {
        EVENT labe = null;
        for(ITEMID itemid : group)
            if(events_map.containsKey(itemid))
                if(labe == null)
                    labe = events_map.get(itemid);
                else
                    if(events_map.get(itemid).CHARTTIME.getTime() > labe.CHARTTIME.getTime())
                        labe = events_map.get(itemid);
        if(labe != null)
            labe.ITEMID = reference.value; // This as our reference ITEMID.
        return labe;
    }
        
    static SIRSScore calculateSIRSScore(Map<ITEMID, EVENT> events_map) {
        SIRSScore sirs_score = new SIRSScore();
        EVENT temperature_c = pickMostRecentTemperatureAndConvertToCelsius(events_map);
        if(temperature_c != null
        && temperature_c.VALUENUM > 0f && (temperature_c.VALUENUM > 38.3f || temperature_c.VALUENUM < 36f))
            sirs_score.addScore(ITEMID.HEDICIM_TEMPERATURE_C, 1);

        if(events_map.containsKey(ITEMID.HEART_RATE))
            if(events_map.get(ITEMID.HEART_RATE).VALUENUM > 90f)
                sirs_score.addScore(ITEMID.HEART_RATE, 1);

        EVENT breath_rate = pickMostRecentFromGroup(events_map, ITEMID.breath_rates(),
            ITEMID.HEDICIM_BREATH_RATE);
        EVENT paco2 = events_map.get(ITEMID.Arterial_PaCO2);
        if(breath_rate != null && breath_rate.VALUENUM > 20f)
            sirs_score.addScore(ITEMID.HEDICIM_BREATH_RATE, 1);
        else if(paco2 != null && paco2.VALUENUM > 0f && paco2.VALUENUM < 32f)
            // Notice that breath rate has preference over the measure of PaCO2 for contributing to score.
            sirs_score.addScore(ITEMID.Arterial_PaCO2, 1);

        EVENT white_blood_cell_count = pickMostRecentFromGroup(events_map, ITEMID.white_blood_cell_counts(),
            ITEMID.HEDICIM_WBC);
        EVENT wbc_bands = pickMostRecentFromGroup(events_map, ITEMID.wbc_band_forms(),
            ITEMID.HEDICIM_WBC_BANDS);
        if(white_blood_cell_count != null && white_blood_cell_count.VALUENUM > 0f
        && (white_blood_cell_count.VALUENUM > 12f || white_blood_cell_count.VALUENUM < 4f))
            sirs_score.addScore(ITEMID.HEDICIM_WBC, 1);
        else if(wbc_bands != null && wbc_bands.VALUENUM > 10f)
            // Notice that white blood cell count has preference over the measure of band forms for contributing to score.
            sirs_score.addScore(ITEMID.HEDICIM_WBC_BANDS, 1);
        return sirs_score;
    }        
}
