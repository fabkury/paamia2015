/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hedicim;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.sql.Driver;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author kuryfs
 */
public class MySQLConnBTRIS {
    private Connection conn = null;
    
    int Labs_exceptions = 0;
    int ICD9_exceptions = 0;
    
    Map<String, ITEMID> btris_to_itemid = new HashMap<>();
    
    String makeBTRISToITEMIDMapKey(String Value, String Observation) {
        return Value + " - " + Observation;
    }
    
    final void buildBTRISToITEMIDMap() {
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PLATELET", "PLATELET"), ITEMID.PLATELET);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("CBC with Differential", "Platelets"), ITEMID.PLATELET);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PRO TIME AUTO", "PRO TIME AUTO"), ITEMID.PT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PTT AUTO", "PTT AUTO"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("CBC", "Platelets"), ITEMID.PLATELET);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PTT", "PTT(Automated)"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("FIBRINOGEN AUTO", "FIBRINOGEN AUTO"), ITEMID.FIBRINOGEN);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PT- INR", "PT - INR"), ITEMID.PT_INR);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PT- INR", "PT(Automated)"), ITEMID.PT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("CBC with Differential", "Platelet Count"), ITEMID.PLATELET);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PT(Automated)", "PT(Automated)"), ITEMID.PT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("F STICK PLATELET", "F STICK PLATELET"), ITEMID.PLATELET);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PRO TIME AUTO MDA", "PRO TIME AUTO MDA"), ITEMID.PT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PTT AUTO MDA", "PTT AUTO MDA"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("FIBRINOGEN FIB", "FIBRINOGEN FIB"), ITEMID.FIBRINOGEN);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PTT(Automated)", "PTT(Automated)"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PRO TIME FIB", "PRO TIME FIB"), ITEMID.PT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PTT FIB", "PTT FIB"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PRO TIME PATIENT", "PRO TIME PATIENT"), ITEMID.PT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PRO TIME CONTROL", "PRO TIME CONTROL"), ITEMID.PT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PTT PATIENT", "PTT PATIENT"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PTT CONTROL", "PTT CONTROL"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("Fibrinogen(Automated)", "Fibrinogen(Automated)"), ITEMID.FIBRINOGEN);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("CBC", "Platelet Count"), ITEMID.PLATELET);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("D-Dimer", "D-Dimer"), ITEMID.D_DIMER);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("Coagulation Panel", "PTT(Automated)"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("Coagulation Panel", "PT(Automated)"), ITEMID.PT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("Platelets", "Platelets"), ITEMID.PLATELET);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("F STICK PLATELET COR", "F STICK PLATELET COR"), ITEMID.PLATELET);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PTT (AUTO)", "PTT (AUTO)"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PRO TIME (AUTO)", "PRO TIME (AUTO)"), ITEMID.PT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("Home Labs", "PLATELET CT"), ITEMID.PLATELET);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PTT Mixing Study", "PTT Mix-1 hour"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PTT Mixing Study", "PTT Mix-Immediate"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PT Mixing Study", "PT Mixing Study"), ITEMID.PT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PT INR", "PT INR"), ITEMID.PT_INR);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("FDP", "FDP"), ITEMID.FDP);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PTT Mixing Study", "PTT Mixing Study"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("FDP-PLASMA", "FDP-PLASMA"), ITEMID.FDP);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("APTT Long Incubation", "APTT Long Incubation"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("Home Labs", "INR-PT"), ITEMID.PT_INR);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("Platelet Count, citrate Plasma", "Platelet Count, citrate Plasma"), ITEMID.PLATELET);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("PT - INR", "PT - INR"), ITEMID.PT_INR);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("Home Labs", "PROTIME"), ITEMID.PT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("FIBRINOGEN", "FIBRINOGEN"), ITEMID.FIBRINOGEN);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("Platelet Panel", "Immature Platelet Fraction"), ITEMID.PLATELET);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("", "PT(Automated)"), ITEMID.PT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("", "PTT(Automated)"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("Home Labs", "PTT"), ITEMID.APTT);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("Platelet Panel", "Platelet Count"), ITEMID.PLATELET);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("", "Fibrinogen(Automated)"), ITEMID.FIBRINOGEN);
        btris_to_itemid.put(makeBTRISToITEMIDMapKey("TECHNICON PLATELET", "TECHNICON PLATELET"), ITEMID.PLATELET);
    }
  
    public MySQLConnBTRIS() throws Exception {
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            conn = DriverManager.getConnection("jdbc:mysql://ADDRESS/DATABASE?" + 
                "user=USERNAME&password=PASSWORD"); // MySQL database connection details intentionally removed.
            buildBTRISToITEMIDMap();
        } catch(ClassNotFoundException | IllegalAccessException | InstantiationException | SQLException e) {
            throw e;
        }
    }
    
    public Set<Patient> getPatients() {
        Set<Patient> patients = new HashSet<>();
        int l = ((Const.debug_patient_number_limiter)!=0)?(Const.debug_patient_number_limiter):(Integer.MAX_VALUE);
        System.out.println("Retrieving patients from table " + Const.BTRIS_TABLE + " whose age is greater than " +
            Const.BTRIS_AGE_CONSIDERED_ADULT + " days.");
        try(ResultSet sql_res = conn.createStatement().executeQuery("select distinct Subject from " + Const.BTRIS_TABLE
            + " WHERE `Data Type`='Labs' AND DATEDIFF(`Date`, `Birth Date`) > " + Const.BTRIS_AGE_CONSIDERED_ADULT)) {
            while(sql_res.next() && (l > 0 || Const.debug_patient_number_limiter == 0))
                if(patients.add(new Patient(DATA_SOURCE.BTRIS, sql_res.getString("Subject"))))
                    l--;
        } catch(Exception e) {
            System.out.println("Exception in MySQLConnBTRIS.getPatients(): "
                + e.getMessage());
        }
            
        System.out.println("Found " + patients.size() + " different patients. (Artificial debugging limit="
            + Const.debug_patient_number_limiter + ", chunk size="+Const.processing_chunk_size+")");

        return patients;
    }
            
    public String buildIDQuery(Set<Patient> patients) {
        String hadm_id_query = new String();
        for(Patient hospitalization : patients)
            hadm_id_query += " OR Subject='" + hospitalization.origin_id + "'";
        return hadm_id_query.substring(" OR ".length()); // Remove the first " OR "
    }
    
    public void getLabs(Set<Patient> patients) {
        int count = 0;
        int ignored_count = 0;
        System.out.println("Querying Subject, Event, Observation, Date, Value, `Unit of Measure` from " + 
            Const.BTRIS_TABLE + " for " + patients.size() + " patients.");
        try(ResultSet sql_res = conn.createStatement().executeQuery(
            // We build a query for the whole current chunk of HADM_IDs for the sole purpose of greatly improving
            // processing performance, since there is a considerable overhead to making a query independently of its size.
            // Notice the ORDER BY CHARTTIME -- this is fundamental for the working of the ISTH DIC calculator implementation
"SELECT Subject, Event, Observation, Date, Value, `Unit of Measure` from " + Const.BTRIS_TABLE + " WHERE ((" +
buildIDQuery(patients) + ") AND `Data Type`='Labs') AND DATEDIFF(`Date`, `Birth Date`) > " +
Const.BTRIS_AGE_CONSIDERED_ADULT)) {
            while(sql_res.next()) {
                String Observation = sql_res.getString("Observation");
                String btris_to_itemid_map_key = makeBTRISToITEMIDMapKey(sql_res.getString("Event"), Observation);
                if(!btris_to_itemid.containsKey(btris_to_itemid_map_key))
                    continue;
                ITEMID itemid = btris_to_itemid.get(btris_to_itemid_map_key);
                String Value = sql_res.getString("Value");
                if(Value == null)
                    // This happens seemingly always with FDP-PLASMA
                    continue;
                Float ValueFloat;
                
                // Handle special cases here.
                if("<0.22".equals(Value)) ValueFloat=0.21f;
                else if("<0.27".equals(Value)) ValueFloat=0.26f;
                else if("<1000".equals(Value)) ValueFloat=999f;
                else if("<5.0".equals(Value)) ValueFloat=4.9f;
                else if(">10.0 - <20.0".equals(Value)) ValueFloat=0f;
                else if(">160.0 - <320.0".equals(Value)) ValueFloat=0f;
                else if(">20.0 - <40.0".equals(Value)) ValueFloat=0f;
                else if(">320.0 - <640.0".equals(Value)) ValueFloat=0f;
                else if(">40.0 - <80.0".equals(Value)) ValueFloat=0f;
                else if(">5.0 - <10.0".equals(Value)) ValueFloat=0f;
                else if(">80.0 - <160.0".equals(Value)) ValueFloat=0f;
                else if("<30".equals(Value)) ValueFloat=29f;
                else if("<60".equals(Value)) ValueFloat=59f;
                else if("<76".equals(Value)) ValueFloat=75f;
                else if("< 10".equals(Value)) ValueFloat=9f;
                else if("<.1".equals(Value)) ValueFloat=0f;
                else if("<1000".equals(Value)) ValueFloat=999f;
                else if("<10.0".equals(Value)) ValueFloat=9f;
                else if("<10.0".equals(Value)) ValueFloat=9f;
                else {
                    // No special case found.
                    try {
                        ValueFloat = (Float)sql_res.getFloat("Value");
                    } catch (Exception ex) {
                        ignored_count++;
                        continue;
                    }
                }
                
                // Then perform unit conversions.
                if(itemid.equals(ITEMID.D_DIMER))
                    ValueFloat *= 1000f;
                
                // Add event to the corresponding patient.
                Patient.map.get(sql_res.getString("Subject")).events.add(new EVENT((Integer)itemid.value,
                    sql_res.getDate("Date"), Value, ValueFloat, "", sql_res.getString("Unit of Measure")));
                count++;
            }
            
            System.out.println("Found " + count + " valid labs corresponding to " + patients.size() + " patients. " +
                ignored_count + " rows were ignored due to impossibility to convert Value to a number.");
        } catch(SQLException e) {
            Labs_exceptions += patients.size();
            System.out.println("!! Exception at getLabs(): " + e.getLocalizedMessage());
        }
    }
    
    public void getICD9(Set<Patient> patients) {
        try(ResultSet sql_res = conn.createStatement().executeQuery("SELECT DISTINCT Subject, Value from " + 
            Const.BTRIS_TABLE + " WHERE `Data Type`='Diagnosis' AND (" + buildIDQuery(patients) + ") AND DATEDIFF(" +
            "`Date`, `Birth Date`) > (2*365) AND Value IS NOT NULL group by Subject, Value")) {
            while(sql_res.next()) {
                String icd9_string = sql_res.getString("Value");
                if(icd9_string.isEmpty()
                    || icd9_string.substring(0,1).matches("[A-Za-z]"))
                    // We ignore all null and all special, non-numeric ICD-9 codes.
                    continue;
                Patient.map.get(sql_res.getString("Subject")).icd9s.add(Float.valueOf(icd9_string));
            }
        } catch(Exception e) {
            ICD9_exceptions += patients.size();
            System.out.println("!! Exception at getICD9(): " + e.getLocalizedMessage());
        }
    }  
}
