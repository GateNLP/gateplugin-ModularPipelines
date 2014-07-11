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
}
