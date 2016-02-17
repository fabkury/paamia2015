/* HEDICIM
 * -------
 * Heparin in Dissiminated Intravascular Coagulation in MIMIC-II
 * By Fabrício Kury -- fabricio.kury@nih.gov
 * 
 * Code start: 2014/25/07 ??:??
 */

package hedicim;

/**
 *
 * @author kuryfs
 */

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class MySQLConnMIMICII {
    private Connection conn = null;

    int LABEVENTS_exceptions = 0;
    int ADMISSIONS_exceptions = 0;
    int ICUSTAY_DETAIL_exceptions = 0;
    int D_PATIENTS_exceptions = 0;
    int POE_ORDER_exceptions = 0;
    int CHARTEVENTS_exceptions = 0;
  
    MySQLConnMIMICII() throws Exception {
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            conn = DriverManager.getConnection(Keys.SQL_CONNECTION_STRING);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | SQLException e) {
            throw e;
        }
    }  
    
    String buildSUBJECT_IDQuery(Set<Patient> patients) {
        String subject_id_query = new String();
        for(Patient patient : patients)
            subject_id_query += " OR SUBJECT_ID=" + patient.subject_id;
        return subject_id_query.substring(" OR ".length()); // Remove the first " OR "
    }
    
    String buildICUSTAY_IDQuery(Set<Patient> patients) {
        String icustay_id_query = new String();
        for(Patient patient : patients)
            for(ICUStay icustay : patient.icustays)
                icustay_id_query += " OR ICUSTAY_ID=" + icustay.id;
        return icustay_id_query.substring(" OR ".length()); // Remove the first " OR "
    }
    
    String buildHADM_IDQuery(Set<Patient> patients) {
        String hadm_id_query = new String();
        for(Patient patient : patients)
            hadm_id_query += " OR HADM_ID=" + patient.origin_id;
        return hadm_id_query.substring(" OR ".length()); // Remove the first " OR "
    }
    
    String buildMEDICATIONQuery(String[] medication_strings) {
        String medication_query = new String();
        for(String medication_string : medication_strings)
            medication_query += " OR LOWER(MEDICATION) LIKE LOWER(" + medication_string + ")";
        return medication_query.substring(" OR ".length()); // Remove the first " OR "
    }
    
    String buildITEMIDQuery(ITEMID[] itemids) {
        String itemid_query = new String();
        for(ITEMID itemid : itemids)
            itemid_query += " OR ITEMID=" + itemid.value;
        return itemid_query.substring(" OR ".length()); // Remove the first " OR "
    }
    
    Set<Patient> getPatientsFromTableICD9(String[] icd9_table_queries) {
        Set<Patient> patients = new HashSet<>();
        int l = ((Const.debug_patient_number_limiter)!=0)?(Const.debug_patient_number_limiter):(Integer.MAX_VALUE);
        for(String icd9_table_query : icd9_table_queries) {
            System.out.println("Querying table ICD9 by: " + icd9_table_query);
            try(ResultSet sql_res = conn.createStatement().executeQuery("select distinct HADM_ID from ICD9 WHERE "
                + icd9_table_query)) {
                while(sql_res.next() && (l > 0 || Const.debug_patient_number_limiter == 0))
                    if(patients.add(new Patient(DATA_SOURCE.MIMICII, sql_res.getString("HADM_ID"))))
                        l--;
                if(l <= 0 && Const.debug_patient_number_limiter != 0)
                    // Break out of the for loop.
                    break;
            } catch(Exception e) {
                System.out.println("Exception in getPatientsFromTableICD9(\"" + icd9_table_query + "\"): "
                    + e.getMessage());
            }
        }

        System.out.println("Found " + patients.size() + " different patients. (Artificial debugging limit="
            + Const.debug_patient_number_limiter + ", chunk size="+Const.processing_chunk_size+")");
        return patients;
    }
    
    void queryADMISSIONS(Set<Patient> patients) {
        // First: fetch hospital admission and discharge dates and times
        try(ResultSet sql_res = conn.createStatement().executeQuery(
"select HADM_ID, SUBJECT_ID, ADMIT_DT, DISCH_DT from ADMISSIONS WHERE " + buildHADM_IDQuery(patients))) {
            while(sql_res.next()) {
                Patient patient = Patient.map.get(sql_res.getString("HADM_ID"));
                patient.admit_dt = new Date(sql_res.getTimestamp("ADMIT_DT").getTime());
                patient.disch_dt = new Date(sql_res.getTimestamp("DISCH_DT").getTime());
                patient.subject_id = sql_res.getInt("SUBJECT_ID");
                Patient.mapSubjectID(patient);
            }
        } catch(SQLException e) {
            ADMISSIONS_exceptions++;
        }
    }
    
    void queryICUSTAY_DETAIL(Set<Patient> patients) {
        // Retrieve SAPSI_MIN, SAPSI_MAX, SOFA_MIN, SOFA_MAX
        try(ResultSet sql_res = conn.createStatement().executeQuery(
"SELECT HADM_ID, ICUSTAY_ID, GENDER, DOB, DOD, EXPIRE_FLG, ICUSTAY_INTIME, ICUSTAY_OUTTIME, ICUSTAY_AGE_GROUP, " +
"HOSPITAL_LOS, ICUSTAY_LOS, SUBJECT_ICUSTAY_TOTAL_NUM, SAPSI_MIN, SAPSI_MAX, SOFA_MIN, SOFA_MAX FROM ICUSTAY_DETAIL " +
"WHERE " + buildHADM_IDQuery(patients))) {
            while(sql_res.next()) {
                Patient patient = Patient.map.get(sql_res.getString("HADM_ID"));
                if(!Const.MIMICII_AGE_GROUP_NEONATE.equals(sql_res.getString("ICUSTAY_AGE_GROUP"))) // NOTICE: From manual review of the MIMIC-II database, there are no patients where
                    // the age group is different from "neonate" and NOT "adult" or "middle". This coding option (to
                    // use different from instead of equal to) is just for a tiny, ok, negligible bit of added efficiency.
                    patient.age_group = Const.AGE_GROUP_ADULT;
                    
                // Use the worst case across all ICU stays inside that hospital admission.
                // NOTICE that the majority of hospital admissions contain only one ICU stay.
                patient.gender = sql_res.getString("GENDER");
                Date dod_d = sql_res.getDate("DOD");
                if(dod_d != null) {
                    Long dod = dod_d.getTime() - patient.admit_dt.getTime();
                    if(patient.dod < 1 || dod < patient.dod)
                        patient.dod = dod;
                }
                int sofa_min = sql_res.getInt("SOFA_MIN");
                int sofa_max = sql_res.getInt("SOFA_MAX");
                int sapsi_min = sql_res.getInt("SAPSI_MIN");
                int sapsi_max = sql_res.getInt("SAPSI_MAX");
                // Retain only the worst case scenario.
                if(patient.sofa_min == 0 || patient.sofa_min > sofa_min)
                    patient.sofa_min = sofa_min;
                
                if(patient.sapsi_min == 0 || patient.sapsi_min > sapsi_min)
                    patient.sapsi_min = sofa_min;
                
                if(patient.sapsi_max < sapsi_max)
                    patient.sapsi_max = sapsi_max;
                
                if(patient.sofa_max < sofa_max)
                    patient.sofa_max = sofa_max;

                patient.hospital_los = sql_res.getInt("HOSPITAL_LOS");
                patient.icustay_total_los += sql_res.getInt("ICUSTAY_LOS");
                patient.icustay_subject_total_num = sql_res.getInt("SUBJECT_ICUSTAY_TOTAL_NUM");
                patient.hospital_expire = sql_res.getString("EXPIRE_FLG");
                                
                Date[] dt = {new Date(sql_res.getTimestamp("ICUSTAY_INTIME").getTime() - patient.admit_dt.getTime()),
                    new Date(sql_res.getTimestamp("ICUSTAY_OUTTIME").getTime() - patient.admit_dt.getTime())};
                patient.addICUStay(new ICUStay(sql_res.getInt("ICUSTAY_ID"), dt));
                long age_on_admit = patient.admit_dt.getTime() - sql_res.getTimestamp("DOB").getTime(); // This is probably the biggest source of exceptions
                age_on_admit /= (long) 1000 * (long) 60 * (long) 60 * (long) 24 * (long) 365;
                if(patient.age_on_admit < age_on_admit)
                    patient.age_on_admit = (int)age_on_admit;
            }
        } catch(SQLException e) {
            ICUSTAY_DETAIL_exceptions++;
        }
    }
    
    void queryD_PATIENTS(Set<Patient> patients) {
        // Calculate early deaths
        // NOTICE: This must be done BEFORE calculating the ISTH scores because they depend on the DOD variable
        // to ignore peri-death lab events.
        try(ResultSet sql_res = conn.createStatement().executeQuery("SELECT SUBJECT_ID, DOD FROM D_PATIENTS WHERE "
            + buildSUBJECT_IDQuery(patients))) {
            // TO DO: Check if there is ever more than one DOD for a given HADM_ID.
            while(sql_res.next()) {
                if(sql_res.getTimestamp("DOD") != null)
                    for(Patient patient : Patient.subject_id_map.get(sql_res.getInt("SUBJECT_ID"))) {
                        Long dod = sql_res.getDate("DOD").getTime() - patient.admit_dt.getTime();
                        if(patient.dod < 1 || dod < patient.dod)
                            patient.dod = dod;
                    }
            }
        } catch(Exception e) {
            D_PATIENTS_exceptions += patients.size();
        }        
    }
    
    void queryPOE_ORDER(Set<Patient> patients) {
        // Retrieve heparin use.
        // Notice: From analyzing MIMIC-II, there are no rows in POE_ORDER related to heparin besides the ones with
        // "heparin", "enoxaparin" or "fondaparinux".
        try(ResultSet sql_res = conn.createStatement().executeQuery(
"select HADM_ID, MEDICATION, ICUSTAY_ID, START_DT, STOP_DT from POE_ORDER WHERE "
+ "(" + buildMEDICATIONQuery(Const.heparin_strings) + ") AND (LOWER(MEDICATION) NOT LIKE '%lush%' AND LOWER(MEDICATION)"
+ "NOT LIKE '%iabp%' AND LOWER(MEDICATION) NOT LIKE '%crrt%' AND LOWER(FREQUENCY) NOT LIKE '%prn%' AND LOWER(ADDITIONAL"
+ "_INSTR) NOT LIKE '%picc%' AND LOWER(ADDITIONAL_INSTR) NOT LIKE '%lush%') AND (" + buildHADM_IDQuery(patients) + ")"))
            {
            while(sql_res.next()) {
                Patient patient = Patient.map.get(sql_res.getString("HADM_ID"));
                Long start_dt_ms = sql_res.getTimestamp("START_DT").getTime() - patient.admit_dt.getTime();
                Long stop_dt_ms = sql_res.getTimestamp("STOP_DT").getTime() - patient.admit_dt.getTime();

                if(stop_dt_ms < patient.ts_first_medication_is_relevant_event - Const.IGNORE_MEDICATION_IF_FAR_FROM_DIC
                    || (patient.ts_last_medication_is_relevant_event > 0 && start_dt_ms > patient.
                    ts_last_medication_is_relevant_event + Const.IGNORE_MEDICATION_IF_FAR_FROM_DIC))
                    // Ignore medications if far from (DIC or near-DIC) event
                    // TO DO: Make this part of the SQL query.
                    continue;

                if(patient.ts_first_adm_of_hep_or_lmwh == 0 || patient.ts_first_adm_of_hep_or_lmwh > start_dt_ms)
                    patient.ts_first_adm_of_hep_or_lmwh = start_dt_ms;

                if(stop_dt_ms <= start_dt_ms)
                     // NOTICE: This is a dirty fix for a problem with the data in MIMIC-II.
                    stop_dt_ms = start_dt_ms + (2*60*60*1000);

                String medication = sql_res.getString("MEDICATION");
                if(medication.matches("(?i)" + Const.heparin_strings[0].replace("'","").replace("%", ".*")))
                    patient.received_heparin = true;
                else if(medication.matches("(?i)" + Const.heparin_strings[1].replace("'","").replace("%", ".*")))
                    patient.received_lmwh = true;
                
                patient.heparin_periods.add(new Long[]{start_dt_ms, stop_dt_ms});
            }
        // TO DO: Parse the start and end times, and administration schedule?
        } catch (Exception e) {
            POE_ORDER_exceptions += patients.size();
            System.out.println("Exception at queryPOE_ORDER(): " + e.getMessage());
        }
    }
    
    void queryLABEVENTS(Set<Patient> patients) {
        int count = 0;
        System.out.println("Querying LABEVENTS table for HADM_ID, LABEVENT_ITEMID, CHARTTIME, VALUE, VALUENUM, FLAG, "
            +"VALUEUOM of " + patients.size() + " patients.");
        try(ResultSet sql_res = conn.createStatement().executeQuery(
            // We build a query for the whole current chunk of HADM_IDs for the sole purpose of greatly improving
            // processing performance, since there is a considerable overhead to making a query independently of its
            // size.
            // Notice the ORDER BY CHARTTIME -- this is fundamental for the working of the ISTH DIC calculator
            // implementation
"SELECT HADM_ID, ITEMID, CHARTTIME, VALUE, VALUENUM, FLAG, VALUEUOM from LABEVENTS WHERE (("
+ buildHADM_IDQuery(patients) + ") AND (" + buildITEMIDQuery(ITEMID.labevents()) + ")) ORDER BY CHARTTIME ASC")) {
            while(sql_res.next()) {
                try {
                    // Hard-code filtering for special cases
                    int itemid = sql_res.getInt("ITEMID");
                    String value = sql_res.getString("VALUE");
                    float valuenum = sql_res.getFloat("VALUENUM");
                    if(itemid == ITEMID.D_DIMER.value || itemid == ITEMID.FIBRINOGEN.value || itemid == ITEMID.FDP.value
                        || itemid == ITEMID.APTT.value || itemid == ITEMID.PT.value) {
                        if(value.contains(">") || value.matches("(?i).*GREATER THAN.*")) {
                            valuenum = Float.valueOf(value.replaceAll("[^0-9.]", "").trim());
                            valuenum *= 1f + 0.05f;
                            value = Float.toString(valuenum);
                        }
                        else if(value.contains("<") || value.matches("(?i).*LESS THAN.*")) {
                            valuenum = Float.valueOf(value.replaceAll("[^0-9.]", "").trim());
                            valuenum *= 1f - 0.05f;
                            value = Float.toString(valuenum);
                        }
                        else if(value.contains("-")) {
                            float left=0, right=0;
                            left = Float.parseFloat(value.substring(0,
                                value.indexOf("-")).replaceAll("[^0-9.,]", "").trim());
                            right = Float.parseFloat(value.substring(value.indexOf("-"),
                                value.length()).replaceAll("[^0-9.,]", "").trim());
                            valuenum = (left+right)/2f;
                            value = Float.toString(valuenum);
                        }
                    }
                    
                    Patient.map.get(sql_res.getString("HADM_ID")).events.add(new EVENT(itemid, sql_res.getDate(
                        "CHARTTIME"),value, valuenum, sql_res.getString("FLAG"), sql_res.getString("VALUEUOM")));
                    count++;
                } catch(Exception ex) {
                    System.out.println("Exception at queryLABEVENTS(): " + ex.getLocalizedMessage());
                }                
            }
            
            System.out.println("Found " + count + " LABEVENTs corresponding to " + patients.size() + " patients.");
        } catch(SQLException e) {
            LABEVENTS_exceptions += patients.size();
        }
    }
    
    void queryCHARTEVENTS(Set<Patient> patients) {
        int count = 0;
        System.out.println("Querying CHARTEVENTS table for ICUSTAY_ID, ITEMID, CHARTTIME, VALUE1, VALUE1NUM, VALUE1UOM "
            + "of " + patients.size() + " patients.");
        try(ResultSet sql_res = conn.createStatement().executeQuery(
            // We build a query for the whole current chunk of HADM_IDs for the sole purpose of greatly improving
            // processing performance, since there is a considerable overhead to making a query independently of its size.
            // Notice the ORDER BY CHARTTIME -- this is fundamental for the working of the ISTH DIC calculator implementation
"SELECT ICUSTAY_ID, ITEMID, CHARTTIME, VALUE1, VALUE1NUM, VALUE1UOM from CHARTEVENTS WHERE ((" + 
buildICUSTAY_IDQuery(patients) + ") AND (" + buildITEMIDQuery(ITEMID.chartvents()) + ")) ORDER BY CHARTTIME ASC")) {
            while(sql_res.next()) {
                Patient patient = Patient.icustay_map.get(sql_res.getInt("ICUSTAY_ID"));
                patient.events.add(new EVENT(
                    (Integer)sql_res.getInt("ITEMID"),
                    sql_res.getDate("CHARTTIME"),
                    sql_res.getString("VALUE1"),
                    (Float)sql_res.getFloat("VALUE1NUM"),
                    "",
                    sql_res.getString("VALUE1UOM")
                    ));
                count++;
            }
            
            System.out.println("Found " + count + " CHARTEVENTs corresponding to " + patients.size() + " patients.");
        } catch(SQLException e) {
            CHARTEVENTS_exceptions += patients.size();
        }
    }

    void queryICD9(Set<Patient> patients) {
        try(ResultSet sql_res = conn.createStatement().executeQuery("SELECT HADM_ID, CODE FROM ICD9 WHERE " +
            buildHADM_IDQuery(patients))) {
            while(sql_res.next()) {
                String icd9_string = sql_res.getString("CODE");
                if(icd9_string.isEmpty()
                    || icd9_string.substring(0,1).matches("[A-Za-z]"))
                    // We ignore all null and all special, non-numeric ICD-9 codes.
                    continue;
                Patient.map.get(sql_res.getString("HADM_ID")).icd9s.add(Float.valueOf(icd9_string));
            }
        } catch(Exception e) {
            D_PATIENTS_exceptions += patients.size();
        }        
    }    
} 