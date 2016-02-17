/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package hedicim;

import java.util.Date;


/**
 *
 * @author kuryfs
 */
public class ICUStay {
    int id = 0;
    Date[] period = null;
    
    ICUStay(int _id, Date[] _period) {
        id = _id;
        period = _period;
    }    
}
