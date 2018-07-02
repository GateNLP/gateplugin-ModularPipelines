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

import gate.Controller;
import gate.FeatureMap;
import gate.ProcessingResource;
import gate.creole.AnalyserRunningStrategy;
import gate.creole.ConditionalController;
import gate.creole.ResourceInstantiationException;
import gate.creole.RunningStrategy;
import gate.util.GateRuntimeException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * Various static utility methods. 
 * 
 * @author Johann Petrak
 */
public class Utils {
  
  protected static final Logger LOGGER = Logger
          .getLogger(Utils.class);
  
  /**
   * Create a config object by reading from the URL or an empty config object
   * if the URL is null, but allow overriding the URL from a property.
   * 
   * This reads a config file from the URL given in the parameter unless 
   * the system property at.ofai.gate.modularpipelines.configFile
   * is set, in which case the configuration will be read from there.
   * If the final configFileUrl to use is null, an empty configuration object
   * is returned. 
   * 
   * @param configFileUrl
   * @return a possibly empty Config instance
   */
  protected static Config readConfigFile(URL configFileUrl) {
    LOGGER.debug("Utils.readConfigFile: Loading config file from "+configFileUrl);
    Config configData = new Config();
    configData.origUrl = configFileUrl;
    File configFile = null;
    String propertyValue = System.getProperty("at.ofai.gate.modularpipelines.configFile");
    if (propertyValue != null && !propertyValue.isEmpty()) {
      configFile = new File(System.getProperty("at.ofai.gate.modularpipelines.configFile"));
    } else if (configFileUrl != null) {
      configFile = gate.util.Files.fileFromURL(configFileUrl);
    } 
    if (configFile != null) {
      if (configFile.toString().endsWith(".yaml")) {
        Yaml yaml = new Yaml();
        FileInputStream is;
        try {
          is = new FileInputStream(configFile);
        } catch (FileNotFoundException ex) {
          throw new GateRuntimeException("Could not open config file, not found: " + configFile);
        }
        Object configsObj = yaml.load(is);
        try {
          is.close();
        } catch (IOException ex) {
          // ignore this
        }
        if (configsObj instanceof List) {
          // we expect each list element to be a map!
          @SuppressWarnings("unchecked")
          List<Object> configs = (List) configsObj;
          for (Object configObj : configs) {
            if (configObj instanceof Map) {
              @SuppressWarnings("unchecked")
              Map<String, Object> config = (Map<String, Object>) configObj;
              String what = (String) config.get("set");
              if (what == null) {
                LOGGER.info("No 'set' key in setting, ignored: " + config);
              } else if (what.equals("prparm")) {
                String controller = (String) config.get("controller");
                String prname = (String) config.get("prname");
                String name = (String) config.get("name");
                Object value = config.get("value");
                if (controller == null || prname == null || name == null) {
                  throw new GateRuntimeException("config setting prparm: controller, prname, or name not given: "+config);
                }
                String prId = controller + "\t" + prname;
                Map<String, Object> prparm = configData.prRuntimeParms.get(prId);
                if (prparm == null) {
                  prparm = new HashMap<>();
                }
                prparm.put(name, value);
                configData.prRuntimeParms.put(prId, prparm);
              } else if (what.equals("prinit")) {
                String controller = (String) config.get("controller");
                String prname = (String) config.get("prname");
                String name = (String) config.get("name");
                Object value = config.get("value");
                if (controller == null || prname == null || name == null) {
                  throw new GateRuntimeException("config setting prinit: controller, prname, or name not given: "+config);
                }
                String prId = controller + "\t" + prname;
                Map<String, Object> prparm = configData.prInitParms.get(prId);
                if (prparm == null) {
                  prparm = new HashMap<>();
                }
                prparm.put(name, value);
                configData.prInitParms.put(prId, prparm);
              } else if (what.equals("prrun")) {
                // we manage the run setting by using the fake PR parameter "$$RUNFLAG$$"
                String controller = (String) config.get("controller");
                String prname = (String) config.get("prname");
                String name = "$$RUNFLAG$$";
                if (controller == null || prname == null) {
                  throw new GateRuntimeException("config setting prparm: controller or prname is not given: "+config);
                }
                Object value = config.get("value");
                if (!(value instanceof Boolean)) {
                  throw new GateRuntimeException("config setting value for prrun is not true or false: "+config);
                }
                String prId = controller + "\t" + prname;
                Map<String, Object> prparm = configData.prRuntimeParms.get(prId);
                if (prparm == null) {
                  prparm = new HashMap<>();
                }
                prparm.put(name, value);
                configData.prRuntimeParms.put(prId, prparm);
              } else if (what.equals("docfeature")) {
                String name = (String) config.get("name");
                Object value = config.get("value");
                if (name == null || value == null) {
                  throw new GateRuntimeException("config setting docfeature: name or value is null: "+config);
                }
                Object overrideObj = config.get("override");
                boolean override = true;  // default is true
                if(overrideObj != null) {
                  // if we do have this, it must be convertable to boolean
                  if(overrideObj instanceof Boolean) {
                    override = (Boolean)overrideObj;
                  } else if(overrideObj instanceof String) {
                    override = Boolean.valueOf((String)overrideObj);
                  } else {
                    throw new GateRuntimeException("Cannot convert value to a boolean for docfeature setting "+name+" for override param value: "+overrideObj);
                  }
                  //System.out.println("Got a value for override for "+name+": "+overrideObj+" set to "+override);
                }
                configData.docFeaturesOverridable.put(name, override);
                configData.docFeatures.put(name, value);
              } else if (what.equals("propset")) {
                String name = (String) config.get("name");
                Object value = config.get("value");
                if (name == null || value == null) {
                  throw new GateRuntimeException("config setting propset: name or value is null");
                }
                String valueString = value.toString();
                System.getProperties().put(name, valueString);
              } else if (what.toLowerCase().equals("inheritconfig")) {
                File fullPath;
                try {
                  fullPath = configFile.getCanonicalFile();
                } catch (IOException ex) {
                  throw new GateRuntimeException("Cannot get canonical pathname for config file "+configFile,ex);
                }
                try {
                  configData.globalConfigFileUrl = fullPath.toURI().toURL();
                } catch (MalformedURLException ex) {
                  throw new GateRuntimeException("Cannot create URL of full path for config file "+configFile,ex);
                }
                LOGGER.debug("Set the global config file url to "+configData.globalConfigFileUrl);
              } else {
                throw new GateRuntimeException("Unknown setting: "+what+" in "+configFile);
              }
            } else {
              LOGGER.info("Config element not a map, ignoring: " + configObj);
            }
          }
        } else {
          throw new GateRuntimeException("Could not read config file, not a list of settings: " + configFile);
        }
      } else {
        throw new GateRuntimeException("Not a supported config file type (.yaml): " + configFile);
      }
    }
    add2ConfigFromProperties(configData);
    return configData;
  }
  
  protected static void add2ConfigFromProperties(Config configData) {
    String prefix = System.getProperty("at.ofai.gate.modularpipelines.propertyPrefix");
    String sep = System.getProperty("at.ofai.gate.modularpipelines.separator");
    if(prefix == null) {
      prefix = "modularpipelines.";
    }
    if(sep == null) {
      sep = ".";
    }
    StringTriple ctlAndPr;
    for(Entry<Object,Object> entry : System.getProperties().entrySet()) {
      String key = (String)entry.getKey();
      if(key.startsWith(prefix)) {
        // check if it also starts with one of the possible setting actions
        if(key.startsWith(prefix+"prparm.")) {
          ctlAndPr = getCtrlPrParm(key,prefix+"prparm.",sep,true);
          String prId = ctlAndPr.s1 + "\t" + ctlAndPr.s2;
          Map<String, Object> prparm = configData.prRuntimeParms.get(prId);
          if (prparm == null) {
            prparm = new HashMap<>();
          }
          prparm.put(ctlAndPr.s3, System.getProperty(key));
          configData.prRuntimeParms.put(prId, prparm);
        } else if(key.startsWith(prefix+"prinit.")) {
          ctlAndPr = getCtrlPrParm(key,prefix+"prinit.",sep,true);
          String prId = ctlAndPr.s1 + "\t" + ctlAndPr.s2;
          Map<String, Object> prparm = configData.prInitParms.get(prId);
          if (prparm == null) {
            prparm = new HashMap<>();
          }
          prparm.put(ctlAndPr.s3, System.getProperty(key));
          configData.prInitParms.put(prId, prparm);          
        } else if(key.startsWith(prefix+"prrun.")) {
          ctlAndPr = getCtrlPrParm(key,prefix+"prrun.",sep,false);
          String prId = ctlAndPr.s1 + "\t" + ctlAndPr.s2;
          Map<String, Object> prparm = configData.prRuntimeParms.get(prId);
          if (prparm == null) {
            prparm = new HashMap<>();
          }
          prparm.put("$$RUNFLAG$$", Boolean.parseBoolean(System.getProperty(key)));
          configData.prRuntimeParms.put(prId, prparm);          
        } else if(key.startsWith(prefix+"docfeature.")) {
          String fname = key.substring((prefix+"docfeature.").length());
          configData.docFeaturesOverridable.put(fname, true);
          configData.docFeatures.put(fname, System.getProperty(key));          
        } else if(key.startsWith(prefix+"udocfeature.")) {
          String fname = key.substring((prefix+"docfeature.").length());
          configData.docFeaturesOverridable.put(fname, false);
          configData.docFeatures.put(fname, System.getProperty(key));          
        } else {
          throw new GateRuntimeException("Odd property with the modular pipelines prefix encountered: "+key);
        }
      }
    }
  }
  
  protected static StringTriple getCtrlPrParm(String key, String prefix, String sep, boolean getParm) {
    String ctl;
    String pr = null;
    String parm = null;
    String rest = key.substring(prefix.length());
    // everything until the first sep is the controller name, everything after that is the pr name
    int sepidx = rest.indexOf(sep);
    if(sepidx < 1) {
      throw new GateRuntimeException("No proper controller name in property "+key);
    }
    ctl = rest.substring(0,sepidx);
    // all that remains after the sep must be the pr name and parm name
    rest = rest.substring(sepidx+sep.length());
    if(rest.isEmpty()) {
      throw new GateRuntimeException("No pr name in property "+key);
    }
    if(getParm) {
      sepidx = rest.indexOf(sep);
      if(sepidx < 1) {
        throw new GateRuntimeException("No parm name in property "+key);
      }
      pr = rest.substring(0,sepidx);
      parm = rest.substring(sepidx+sep.length());
    } else {
      pr = rest;
    }
    //System.out.println("DEBUG: getetting for key="+key+" prefix="+prefix+" flag="+getParm);
    //System.out.println("DEBUG: returning ctl="+ctl+" pr="+pr+" parm="+parm);
    return new StringTriple(ctl,pr,parm);
    
  }
  
  protected static class StringTriple {
    public String s1;
    public String s2;
    public String s3;
    public StringTriple(String v1, String v2, String v3) { s1 = v1; s2 = v2; s3 = v3; }
    public StringTriple(String v1, String v2) { s1 = v1; s2 = v2;  }
  }
  
  
  // NOTE: this method should be thread-safe!!!
  protected static void setControllerParms(Controller cntrlr, Config config) {
    LOGGER.debug("Setting controller parms for " + cntrlr.getName());
    // we store both the actual runtime parameters and the run modes in 
    // config.prRuntimeParms so this is != null if either or both are set
    // in the config.
    if (config.prRuntimeParms != null) {
      String cName = cntrlr.getName();
      ConditionalController condController = null;
      List<ProcessingResource> prs;
      List<RunningStrategy> strategies = null;
      if (cntrlr instanceof ConditionalController) {
        condController = (ConditionalController) cntrlr;
        strategies = condController.getRunningStrategies();
      } 
      prs = (List<ProcessingResource>) cntrlr.getPRs();
      // create a map that maps names to prs for this controller
      Map<String, Integer> prNums = new HashMap<>();
      int i = 0;
      for (ProcessingResource pr : prs) {
        String id = cName + "\t" + pr.getName();
        if (prNums.containsKey(id)) {
          throw new GateRuntimeException("Cannot set PR parameters the PR name appears twice: " + id);
        }
        prNums.put(id, i);
        i++;
      }
      // set the PR runtime parameters 
      for (String prId : config.prRuntimeParms.keySet()) {
        String[] contrprname = prId.split("\t");
        if (contrprname[0].equals(cName)) {
          Integer id = prNums.get(prId);
          if (id == null) {
            throw new GateRuntimeException("Cannot set PR parameter, no PR found with id: " + prId);
          }
          ProcessingResource pr = prs.get(id);
          Map<String, Object> prparm = config.prRuntimeParms.get(prId);
          for (String parmName : prparm.keySet()) {
            Object parmValue = prparm.get(parmName);
            LOGGER.debug("Debug: trying to process PR setting " + parmValue + " for parm " + parmName + " in PR " + prId + " of " + cName);
            if (parmName.equals("$$RUNFLAG$$")) {
              LOGGER.debug("Trying to set a runflag");
              if (condController != null) {
                //System.out.println("DEBUG: Setting runflag "+parmName+" to "+parmValue+" for "+prId);
                boolean flag = (Boolean) parmValue;
                AnalyserRunningStrategy str = (AnalyserRunningStrategy) strategies.get(id);
                LOGGER.debug("Setting the run mode: " + flag);
                str.setRunMode(flag ? AnalyserRunningStrategy.RUN_ALWAYS : AnalyserRunningStrategy.RUN_NEVER);
              }
            } else {
              try {
                pr.setParameterValue(parmName, parmValue);
              } catch (ResourceInstantiationException ex) {
                throw new GateRuntimeException("Could not set parameter " + parmName + " for PR id " + prId + " to value " + parmValue,ex);
              }
            }
          } // for parmName : prparm.keySet
        } // if controller names match
      }
    } else {
      LOGGER.debug("prRuntimeParms is null!");
    }
  } // method setControllerParms
  
  /**
   * Set values in the feature map based on the document feature settings of
   * the config.
   * 
   * @param theFeatures
   * @param config 
   */
  protected static void setDocumentFeatures(FeatureMap theFeatures, Config config) {
    if(config.docFeatures != null) {
      for(Object keyObj : config.docFeatures.keySet()) {
        String key = (String)keyObj;
        // only set it unless we have a flag not to override an existing value
        // and there is an existing value
        if(theFeatures.get(key) != null) {
          // there is a value, make sure we do not have a flag that 
          // prevents overriding the value
          // If we do not have any information or if the info is set to true,
          // override, otherwise (we do have information and it is false) 
          // do not override.
          if(config.docFeaturesOverridable == null ||
             config.docFeaturesOverridable.get(keyObj) == null ||
             ((Boolean)config.docFeaturesOverridable.get(keyObj))
            ) {
            //System.out.println("Setting document feature, there was a value already: "+key+" to "+config.docFeatures.get(keyObj));
            //System.out.println("overridable was "+config.docFeaturesOverridable);
            theFeatures.put(key,config.docFeatures.get(keyObj));
          } else {
            //System.out.println("NOT setting document feature, there was a value already: "+key+" to "+config.docFeatures.get(keyObj));
          }
        } else {
          // there is no value yet, we can set it
          //System.out.println("Setting document feature, there was no value yet: "+key+" to "+config.docFeatures.get(keyObj));
          theFeatures.put(key,config.docFeatures.get(keyObj));
        }
      }
    }
  }
  
}
