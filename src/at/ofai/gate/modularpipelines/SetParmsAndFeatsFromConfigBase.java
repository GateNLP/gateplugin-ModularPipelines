package at.ofai.gate.modularpipelines;

import gate.Controller;
import gate.FeatureMap;
import gate.GateConstants;
import gate.ProcessingResource;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.AnalyserRunningStrategy;
import gate.creole.ConditionalController;
import gate.creole.ResourceInstantiationException;
import gate.creole.RunningStrategy;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.Optional;
import gate.gui.ActionsPublisher;
import gate.gui.MainFrame;
import gate.util.GateRuntimeException;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.swing.AbstractAction;
import javax.swing.Action;
import static javax.swing.Action.SHORT_DESCRIPTION;
import org.yaml.snakeyaml.Yaml;

public class SetParmsAndFeatsFromConfigBase extends AbstractLanguageAnalyser
  implements ActionsPublisher {

  // For PRs, the property file must contain settings in the following form:
  // prparm.settingid.prname = "name of the PR in the controller"
  // prparm.settingid.name = "name of the parameter/setting for the pr"
  // prparm.settingid.value = "value to set the parameter to"
  // prparm.settingid.controller = "name of the controller" 
  // anythitng that is valid as a prefix of a property name can be used instead
  // of "pr1"
  // For document features:
  // docfeature.somefeaturenametoset = "the value"
  // For properties:
  // propset.the.property.name = "the value"
  // For Yaml files, each setting needs to be a map and settings must be 
  // included as a list at the top-level of the file. Each setting must 
  // have the "set" key which must be one of prparm, docfeature or propset:
  // - set: prparm
  //   controller: controllerName
  //   prname: processingResourceName
  //   name: parameterName
  //   value: valueToSet
  // - set: docfeature
  //   name: featurename
  //   value: featurevalue
  // - set: propset
  //   name: propertyName
  //   value: propertyValue
  // - set: prrun
  //   controller: controllerName
  //   prname: processingResourceName
  //   value: true/false
  @Optional
  @CreoleParameter(
          comment = "The URL of the config file for setting parameters and features (.properties or .yaml)",
          suffixes = "properties;yaml")
  public void setConfigFileUrl(URL fileUrl) {
    configFileUrl = fileUrl;
  }

  public URL getConfigFileUrl() {
    return configFileUrl;
  }
  protected URL configFileUrl = null;

  @Override
  public Resource init() throws ResourceInstantiationException {
    readConfigFile();
    return this;
  }
  protected FeatureMap docFeaturesFromConfig = null;
  // PR parameters are stored as a map from (Controller name, PR name) to parmsettings for that PR
  // where the parmsettings are represented as a map from parm name to parm
  // value
  protected Map<String, Map<String, Object>> prParms = null;

  protected void readConfigFile() {
    // first read the document features property file
    File configFile = null;
    if (getConfigFileUrl() != null) {
      configFile = gate.util.Files.fileFromURL(getConfigFileUrl());
    } else if (System.getProperty("modularpipelines.configFile") != null) {
      configFile = new File(System.getProperty("modularpipelines.configFile"));
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
        docFeaturesFromConfig = gate.Factory.newFeatureMap();
        prParms = new HashMap<String, Map<String, Object>>();

        // check all the keys in the property file and see if they match the
        // name pattern for one of the things we already support
        for (String key : properties.stringPropertyNames()) {
          if (key.startsWith("docfeature.")) {
            docFeaturesFromConfig.put(key.replaceAll("^docfeature\\.", ""), properties.getProperty(key));
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
            Map<String, Object> prparm = prParms.get(prId);
            if (prparm == null) {
              prparm = new HashMap<String, Object>();
            }
            prparm.put(prParm, prValue);
            prParms.put(prId, prparm);
          } else if (key.startsWith("propset")) {
            System.getProperties().put(key.substring("propset".length()), properties.getProperty(key));
          } else { // startswith prparm
            throw new GateRuntimeException("setting does not start with a known prefix: " + key);
          }
        }
      } else if (configFile.toString().endsWith(".yaml")) {
        Yaml yaml = new Yaml();
        docFeaturesFromConfig = gate.Factory.newFeatureMap();
        prParms = new HashMap<String, Map<String, Object>>();
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
                System.err.println("No 'set' key in setting, ignored: " + config);
              } else if (what.equals("prparm")) {
                String controller = (String) config.get("controller");
                String prname = (String) config.get("prname");
                String name = (String) config.get("name");
                Object value = config.get("value");
                if (controller == null || prname == null || name == null) {
                  throw new GateRuntimeException("config setting prparm: controller, prname, or name not given: "+config);
                }
                String prId = controller + "\t" + prname;
                Map<String, Object> prparm = prParms.get(prId);
                if (prparm == null) {
                  prparm = new HashMap<String, Object>();
                }
                prparm.put(name, value);
                prParms.put(prId, prparm);
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
                Map<String, Object> prparm = prParms.get(prId);
                if (prparm == null) {
                  prparm = new HashMap<String, Object>();
                }
                prparm.put(name, value);
                prParms.put(prId, prparm);
              } else if (what.equals("docfeature")) {
                String name = (String) config.get("name");
                Object value = config.get("value");
                if (name == null || value == null) {
                  throw new GateRuntimeException("config setting docfeature: name or value is null: "+config);
                }
                docFeaturesFromConfig.put(name, value);
              } else if (what.equals("propset")) {
                String name = (String) config.get("name");
                String value = (String) config.get("value");
                if (name == null || value == null) {
                  throw new GateRuntimeException("config setting propset: name or value is null");
                }
                System.getProperties().put(name, value);
              }
            } else {
              System.err.println("Config element not a map, ignoring: " + configObj);
            }
          }
        } else {
          throw new GateRuntimeException("Could not read config file, not a list of settings: " + configFile);
        }
      } else {
        throw new GateRuntimeException("Not a supported config file type (.properties and .yaml): " + configFile);
      }
    }

  }

  protected void setControllerParms(Controller cntrlr) {
    //System.out.println("Setting controller parms for " + cntrlr.getName());
    if (prParms != null) {
      String cName = cntrlr.getName();
      ConditionalController condController = null;
      List<ProcessingResource> prs = null;
      List<RunningStrategy> strategies = null;
      if (cntrlr instanceof ConditionalController) {
        //System.out.println("DEBUG: it is a conditional controller!");
        condController = (ConditionalController) cntrlr;
        strategies = condController.getRunningStrategies();
      } else {
        //System.out.println("NOT a conditional controller");
      }
      prs = (List<ProcessingResource>) cntrlr.getPRs();
      // create a map that maps names to prs for this controller
      Map<String, Integer> prNums = new HashMap<String, Integer>();
      int i = 0;
      for (ProcessingResource pr : prs) {
        String id = cName + "\t" + pr.getName();
        if (prNums.containsKey(id)) {
          throw new GateRuntimeException("Cannot set PR parameters the PR name appears twice: " + name);
        }
        // System.out.println("Adding PR id: "+id);
        prNums.put(id, i);
        i++;
      }
      // set the PR runtime parameters 
      for (String prId : prParms.keySet()) {
        //System.out.println("Checking ID: " + prId + " controller is " + cName);
        String[] contrprname = prId.split("\t");
        if (contrprname[0].equals(cName)) {
          int id = prNums.get(prId);
          ProcessingResource pr = prs.get(id);
          if (pr == null) {
            throw new GateRuntimeException("Cannot set PR parameter, no PR found with id: " + prId);
          }
          Map<String, Object> prparm = prParms.get(prId);
          for (String parmName : prparm.keySet()) {
            Object parmValue = prparm.get(parmName);
            //System.out.println("Debug: trying to process PR setting " + parmValue + " for parm " + parmName + " in PR " + prId + " of " + cName);
            if (parmName.equals("$$RUNFLAG$$")) {
              //System.out.println("Trying to set a runflag");
              if (condController != null) {
                boolean flag = (Boolean) parmValue;
                AnalyserRunningStrategy str = (AnalyserRunningStrategy) strategies.get(id);
                //System.out.println("Setting the run mode: " + flag);
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
      //System.out.println("prParms is null!");
    }
  } // method setControllerParms
  
  private List<Action> actions;
  
  @Override
  public List<Action> getActions() {
    if (actions == null) {
      actions = new ArrayList<Action>();
      actions.add(
              new AbstractAction("Re-load config") {
        {
          putValue(SHORT_DESCRIPTION,
                  "Re-load the configuration file, if there is one");
          putValue(GateConstants.MENU_PATH_KEY,
                  new String[]{"WTFISTHIS??????"});
        }
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent evt) {
          Runnable runnableAction = new Runnable() {
            @Override
            public void run() {
              try {
                MainFrame.lockGUI("Reading config file "+getConfigFileUrl()+"...");
                readConfigFile();
              } finally {
                MainFrame.unlockGUI();
              }
            }
          };
          Thread thread = new Thread(runnableAction, "ModularPipelinesConfigurationReReader");
          thread.start();
        };
      });
    }
    return actions;
  }
  
  
  
}
