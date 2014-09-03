/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ofai.gate.modularpipelines;

import gate.Controller;
import gate.ProcessingResource;
import gate.creole.AnalyserRunningStrategy;
import gate.creole.ConditionalController;
import gate.creole.ResourceInstantiationException;
import gate.creole.RunningStrategy;
import gate.util.GateRuntimeException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author Johann Petrak
 */
public class Utils {
  
  protected static final Logger logger = Logger
          .getLogger(Utils.class);
  
  protected static Config readConfigFile(URL configFileUrl) {
    // first read the document features property file
    logger.debug("DEBUG: trying to read config from "+configFileUrl);
    Config configData = new Config();
    File configFile = null;
    String propertyValue = System.getProperty("at.ofai.gate.modularpipelines.configFile");
    if (propertyValue != null && !propertyValue.isEmpty()) {
      configFile = new File(System.getProperty("at.ofai.gate.modularpipelines.configFile"));
    } else if (configFileUrl != null) {
      configFile = gate.util.Files.fileFromURL(configFileUrl);
    } 
    if (configFile != null) {
      if (configFile.toString().endsWith(".properties")) {
        Properties properties = new Properties();
        try {
          properties.load(new FileReader(configFile));
        } catch (IOException ex) {
          throw new GateRuntimeException("Could not read properties file " + configFile, ex);
        }
        // convert the properties to features
        configData.docFeatures = gate.Factory.newFeatureMap();
        configData.prRuntimeParms = new HashMap<String, Map<String, Object>>();

        // check all the keys in the property file and see if they match the
        // name pattern for one of the things we already support
        for (String key : properties.stringPropertyNames()) {
          if (key.startsWith("docfeature.")) {
            configData.docFeatures.put(key.replaceAll("^docfeature\\.", ""), properties.getProperty(key));
          } else if (key.startsWith("prparm.")) {
            String settingId = key.substring("prparm.".length());
            settingId = settingId.replaceAll("\\.[^.]+$", "");
            String nameProp = "prparm." + settingId + ".prname";
            String parmProp = "prparm." + settingId + ".name";
            String valueProp = "prparm." + settingId + ".value";
            String contrProp = "prparm." + settingId + ".controller";
            String prName = properties.getProperty(nameProp, null);
            String prParm = properties.getProperty(parmProp, null);
            String prValue = properties.getProperty(valueProp, null);
            String prContr = properties.getProperty(contrProp, null);
            if (prName == null) {
              throw new GateRuntimeException("No property specifying pr name (.prname) for pr parameter setting " + key);
            }
            if (prParm == null) {
              throw new GateRuntimeException("No property specifying  parameter name (.name) for pr parameter setting " + key);
            }
            if (prValue == null) {
              throw new GateRuntimeException("No property specifying parameter value (.value) for pr parameter setting " + key);
            }
            if (prContr == null) {
              throw new GateRuntimeException("No property specifying controller name (.controller) for pr parameter setting " + key);
            }
            String prId = prContr + "\t" + prName;
            Map<String, Object> prparm = configData.prRuntimeParms.get(prId);
            if (prparm == null) {
              prparm = new HashMap<String, Object>();
            }
            prparm.put(prParm, prValue);
            configData.prRuntimeParms.put(prId, prparm);
          } else if (key.startsWith("propset")) {
            System.getProperties().put(key.substring("propset".length()), properties.getProperty(key));
          } else { // startswith prparm
            throw new GateRuntimeException("setting does not start with a known prefix: " + key);
          }
        }
      } else if (configFile.toString().endsWith(".yaml")) {
        Yaml yaml = new Yaml();
        configData.docFeatures = gate.Factory.newFeatureMap();
        configData.prRuntimeParms = new HashMap<String, Map<String, Object>>();
        configData.prInitParms = new HashMap<String, Map<String, Object>>();
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
          List<Object> configs = (List) configsObj;
          for (Object configObj : configs) {
            if (configObj instanceof Map) {
              Map<String, Object> config = (Map<String, Object>) configObj;
              String what = (String) config.get("set");
              if (what == null) {
                logger.info("No 'set' key in setting, ignored: " + config);
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
                  prparm = new HashMap<String, Object>();
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
                  prparm = new HashMap<String, Object>();
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
                  prparm = new HashMap<String, Object>();
                }
                prparm.put(name, value);
                configData.prRuntimeParms.put(prId, prparm);
              } else if (what.equals("docfeature")) {
                String name = (String) config.get("name");
                Object value = config.get("value");
                if (name == null || value == null) {
                  throw new GateRuntimeException("config setting docfeature: name or value is null: "+config);
                }
                configData.docFeatures.put(name, value);
              } else if (what.equals("propset")) {
                String name = (String) config.get("name");
                Object value = config.get("value");
                if (name == null || value == null) {
                  throw new GateRuntimeException("config setting propset: name or value is null");
                }
                String valueString = null;
                if(value != null) {
                  valueString = value.toString();
                }
                System.getProperties().put(name, valueString);
              }
            } else {
              logger.info("Config element not a map, ignoring: " + configObj);
            }
          }
        } else {
          throw new GateRuntimeException("Could not read config file, not a list of settings: " + configFile);
        }
      } else {
        throw new GateRuntimeException("Not a supported config file type (.properties and .yaml): " + configFile);
      }
    }
    return configData;
  }
  
  // NOTE: this method should be thread-safe!!!
  protected static void setControllerParms(Controller cntrlr, Config config) {
    logger.debug("Setting controller parms for " + cntrlr.getName());
    // we store both the actual runtime parameters and the run modes in 
    // config.prRuntimeParms so this is != null if either or both are set
    // in the config.
    if (config.prRuntimeParms != null) {
      String cName = cntrlr.getName();
      ConditionalController condController = null;
      List<ProcessingResource> prs = null;
      List<RunningStrategy> strategies = null;
      if (cntrlr instanceof ConditionalController) {
        condController = (ConditionalController) cntrlr;
        strategies = condController.getRunningStrategies();
      } 
      prs = (List<ProcessingResource>) cntrlr.getPRs();
      // create a map that maps names to prs for this controller
      Map<String, Integer> prNums = new HashMap<String, Integer>();
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
            logger.debug("Debug: trying to process PR setting " + parmValue + " for parm " + parmName + " in PR " + prId + " of " + cName);
            if (parmName.equals("$$RUNFLAG$$")) {
              logger.debug("Trying to set a runflag");
              if (condController != null) {
                boolean flag = (Boolean) parmValue;
                AnalyserRunningStrategy str = (AnalyserRunningStrategy) strategies.get(id);
                logger.debug("Setting the run mode: " + flag);
                str.setRunMode(flag ? AnalyserRunningStrategy.RUN_ALWAYS : AnalyserRunningStrategy.RUN_NEVER);
              }
            } else {
              try {
                pr.setParameterValue(parmName, parmValue);
              } catch (ResourceInstantiationException ex) {
                throw new GateRuntimeException("Could not set parameter " + parmName + " for PR id " + prId + " to value " + parmValue);
              }
            }
          } // for parmName : prparm.keySet
        } // if controller names match
      }
    } else {
      logger.debug("prRuntimeParms is null!");
    }
  } // method setControllerParms
  
  
  
}
