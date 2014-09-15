package at.ofai.gate.modularpipelines;

import gate.FeatureMap;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 *
 * @author johann
 */
public class Config {
  private static final Logger logger = Logger.getLogger("Config");
  public Config() {
    logger.debug("Config: creating new");
  }
  // If this is non-null it signals that any sub-pipeline of the pipeline
  // which has this set should have their config file URL set to this too.
  // This is used to allow for an outer config file to set the config file 
  // of all inner pipelines to itself. 
  URL globalConfigFileUrl = null;
  // the orig URL is set to the URL that was given originally when this 
  // config file was read and is used to prevent that the same config file
  // is read several times.
  URL origUrl = null;
  FeatureMap docFeatures = gate.Factory.newFeatureMap();
  FeatureMap docFeaturesOverridable = gate.Factory.newFeatureMap();
  // The prRuntimeParms map has as keys strings of the form "controllerName\tprName"
  // and maps those to a map that contains as key the parameter name and 
  // as value the parameter value
  Map<String,Map<String,Object>> prRuntimeParms = new HashMap<String, Map<String, Object>>();
  // Same, but for init parms
  Map<String,Map<String,Object>> prInitParms = new HashMap<String, Map<String, Object>>();
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("URL="+origUrl);
    sb.append(" ");
    sb.append("docFeatures: ");
    if(docFeatures != null) {
      sb.append(docFeatures.toString());
    } else {
      sb.append("null");
    }    
    sb.append("; runtimeParms: ");
    if(prRuntimeParms != null) {
    for(String key : prRuntimeParms.keySet()) {
      Map<String,Object> val = prRuntimeParms.get(key);
      sb.append(key);
      sb.append("=");
      for(String k : val.keySet()) {
        sb.append(k);
        sb.append(":");
        sb.append(val.get(k).toString());
        sb.append(" ");
      }
    }
    } else {
      sb.append("null");
    }
    sb.append("; initParms: ");
    if(prInitParms != null) {
    for(String key : prRuntimeParms.keySet()) {
      Map<String,Object> val = prInitParms.get(key);
      sb.append(key);
      sb.append("=");
      if(val != null) {
      for(String k : val.keySet()) {
        sb.append(k);
        sb.append(":");
        sb.append(val.get(k).toString());
        sb.append(" ");
      }
      } else {
        sb.append("null");
      }
    }
    } else {
      sb.append("null");
    }
    sb.append(" ");
    sb.append("globalConfigFileUrl="+globalConfigFileUrl);
    return sb.toString();
  }
}
