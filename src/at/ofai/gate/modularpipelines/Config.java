/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ofai.gate.modularpipelines;

import gate.FeatureMap;
import java.util.Map;

/**
 *
 * @author johann
 */
public class Config {
  FeatureMap docFeatures = null;
  // The prRuntimeParms map has as keys strings of the form "controllerName\tprName"
  // and maps those to a map that contains as key the parameter name and 
  // as value the parameter value
  Map<String,Map<String,Object>> prRuntimeParms = null;
  // Same, but for init parms
  Map<String,Map<String,Object>> prInitParms = null;
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("docFeatures: ");
    if(docFeatures != null) {
      sb.append(docFeatures.toString());
    } else {
      sb.append("null");
    }
    sb.append("runtimeParms: ");
    if(prRuntimeParms != null) {
    for(String key : prRuntimeParms.keySet()) {
      Map<String,Object> val = prRuntimeParms.get(key);
      sb.append(key);
      sb.append("=");
      for(String k : val.keySet()) {
        sb.append(k);
        sb.append(":");
        sb.append(val.get(k).toString());
      }
    }
    } else {
      sb.append("null");
    }
    sb.append("initParms: ");
    if(prInitParms != null) {
    for(String key : prRuntimeParms.keySet()) {
      Map<String,Object> val = prInitParms.get(key);
      sb.append(key);
      sb.append("=");
      for(String k : val.keySet()) {
        sb.append(k);
        sb.append(":");
        sb.append(val.get(k).toString());
      }
    }
    } else {
      sb.append("null");
    }
    return sb.toString();
  }
}
