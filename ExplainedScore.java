/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package hedicim;

import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author kuryfs
 */
public class ExplainedScore {
    Double score = 0d;
    EnumMap<ITEMID, Double> itemids = new EnumMap<>(ITEMID.class);    
    Date date = new Date();
    void addScore(ITEMID itemid, double score_to_add) {
        score += score_to_add;
        itemids.put(itemid, score_to_add);
    }
}
