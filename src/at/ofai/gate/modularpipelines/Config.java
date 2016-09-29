/*
 * Copyright (c) 2013 Austrian Research Institute for Artificial Intelligence (OFAI). 
 * Copyright (C) 2014-2016 The University of Sheffield.
 *
 * This file is part of gateplugin-ModularPipelines
 * (see https://github.com/johann-petrak/gateplugin-ModularPipelines)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software. If not, see <http://www.gnu.org/licenses/>.
 */
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
        if(val.get(k) == null) {
          sb.append("(null)");
        } else {
          sb.append(val.get(k).toString());
        }
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
