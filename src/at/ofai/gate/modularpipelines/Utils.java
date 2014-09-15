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
import org.apache.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author Johann Petrak
 */
public class Utils {
  
  protected static final Logger logger = Logger
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
    logger.debug("Utils.readConfigFile: Loading config file from "+configFileUrl);
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
                String valueString = null;
                if(value != null) {
                  valueString = value.toString();
                }
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
                logger.debug("Set the global config file url to "+configData.globalConfigFileUrl);
              } else {
                throw new GateRuntimeException("Unknown setting: "+what+" in "+configFile);
              }
            } else {
              logger.info("Config element not a map, ignoring: " + configObj);
            }
          }
        } else {
          throw new GateRuntimeException("Could not read config file, not a list of settings: " + configFile);
        }
      } else {
        throw new GateRuntimeException("Not a supported config file type (.yaml): " + configFile);
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
