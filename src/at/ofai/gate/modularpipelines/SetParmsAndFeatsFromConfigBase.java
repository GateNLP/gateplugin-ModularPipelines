package at.ofai.gate.modularpipelines;

import gate.Controller;
import gate.FeatureMap;
import gate.ProcessingResource;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.Optional;
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
import org.yaml.snakeyaml.Yaml;


public class SetParmsAndFeatsFromConfigBase extends AbstractLanguageAnalyser {

  
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
  
  @Optional
  @CreoleParameter(
          comment="The URL of the config file for setting parameters and features (.properties or .yaml)",
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
  protected Map<String,Map<String,Object>> prParms = null;
  
  protected void readConfigFile() {
    // first read the document features property file
    File configFile = null;
    if(getConfigFileUrl() != null) {
      configFile = gate.util.Files.fileFromURL(getConfigFileUrl());
    } else if(System.getProperty("modularpipelines.configFile") != null) {
      configFile = new File(System.getProperty("modularpipelines.configFile"));
    }
    if(configFile != null) {
      if(configFile.toString().endsWith(".properties")) {
      Properties properties = new Properties();
      try {
        properties.load(new FileReader(configFile));
      } catch (IOException ex) {
        throw new GateRuntimeException("Could not read properties file "+configFile,ex);
      }
      // convert the properties to features
      docFeaturesFromConfig = gate.Factory.newFeatureMap();
      prParms = new HashMap<String,Map<String,Object>>();
      
      // check all the keys in the property file and see if they match the
      // name pattern for one of the things we already support
      for(String key : properties.stringPropertyNames()) {
        if(key.startsWith("docfeature.")) {
          docFeaturesFromConfig.put(key.replaceAll("^docfeature\\.", ""), properties.getProperty(key));
        } else if(key.startsWith("prparm.")) {
          String settingId = key.substring("prparm.".length());
          settingId = settingId.replaceAll("\\.[^.]+$", "");
          String nameProp = "prparm."+settingId+".prname";
          String parmProp = "prparm."+settingId+".name";
          String valueProp = "prparm."+settingId+".value";
          String contrProp = "prparm."+settingId+".controller";
          String prName = properties.getProperty(nameProp,null);
          String prParm = properties.getProperty(parmProp,null);
          String prValue = properties.getProperty(valueProp,null);
          String prContr = properties.getProperty(contrProp,null);
          if(prName == null) {
            throw new GateRuntimeException("No property specifying pr name (.prname) for pr parameter setting "+key);
          }
          if(prParm == null) {
            throw new GateRuntimeException("No property specifying  parameter name (.name) for pr parameter setting "+key);
          }
          if(prValue == null) {
            throw new GateRuntimeException("No property specifying parameter value (.value) for pr parameter setting "+key);
          }
          if(prContr == null) {
            throw new GateRuntimeException("No property specifying controller name (.controller) for pr parameter setting "+key);
          }
          String prId = prContr+"\t"+prName;
          Map<String,Object> prparm = prParms.get(prId);
          if(prparm == null) {
            prparm = new HashMap<String,Object>();
          }
          prparm.put(prParm, prValue);
          prParms.put(prId,prparm);
        } else if(key.startsWith("propset")) {
          System.getProperties().put(key.substring("propset".length()), properties.getProperty(key));
        } else { // startswith prparm
          throw new GateRuntimeException("setting does not start with a known prefix: "+key);
        }
      }
      } else if(configFile.toString().endsWith(".yaml")) {
        Yaml yaml = new Yaml();
        docFeaturesFromConfig = gate.Factory.newFeatureMap();
        prParms = new HashMap<String,Map<String,Object>>();        
        FileInputStream is;
        try {
          is = new FileInputStream(configFile);
        } catch (FileNotFoundException ex) {
          throw new GateRuntimeException("Could not open config file, not found: "+configFile);
        }
        Object configsObj = yaml.load(is);
        try {
          is.close();
        } catch (IOException ex) {
          // ignore this
        }
        if(configsObj instanceof List) {
          // we expect each list element to be a map!
          List<Object> configs = (List)configsObj;
          for(Object configObj : configs) {
            if(configObj instanceof Map) {
              Map<String,Object> config = (Map<String,Object>)configObj;
              String what = (String)config.get("set");
              if(what == null) {
                System.err.println("No 'set' key in setting, ignored: "+config);
              } else if(what.equals("prparm")) {
                // TODO: check required keys for non-null!!!!
                String controller = (String)config.get("controller");
                String prname = (String)config.get("prname");
                String parm = (String)config.get("name");
                Object value =  config.get("value");
                if(controller == null || prname == null || parm == null ) {
                  throw new GateRuntimeException("config setting prparm: controller, prname, name, or value is null");
                }
                String prId = controller+"\t"+prname;
                Map<String,Object> prparm = prParms.get(prId);
                if(prparm == null) {
                  prparm = new HashMap<String,Object>();
                }
                prparm.put(parm, value);
                prParms.put(prId,prparm);                                
              } else if(what.equals("docfeature")) {
                String name = (String)config.get("name");
                Object value = config.get("value");
                if(name == null || value == null) {
                  throw new GateRuntimeException("config setting docfeature: name or value is null");
                }
                docFeaturesFromConfig.put(name,value);
              } else if(what.equals("propset")) {
                String name = (String)config.get("name");
                String value = (String)config.get("value");
                if(name == null || value == null) {
                  throw new GateRuntimeException("config setting propset: name or value is null");
                }
                System.getProperties().put(name,value);
              }
            } else {
              System.err.println("Config element not a map, ignoring: "+configObj);
            }
          }
        } else {
          throw new GateRuntimeException("Could not read config file, not a list of settings: "+configFile);
        }
      } else {
        throw new GateRuntimeException("Not a supported config file type (.properties and .yaml): "+configFile);
      }
    }
    
  }
  
  
  protected void setControllerParms(Controller cntrlr) {
    if(prParms != null) {
    String cName = cntrlr.getName();
    // create a map that maps names to prs for this controller
    Map<String, ProcessingResource> prids = new HashMap<String, ProcessingResource>();
    for(ProcessingResource pr : cntrlr.getPRs()) {
      String id = cName + "\t" + pr.getName();
      if(prids.containsKey(id)) {
        throw new GateRuntimeException("Cannot set PR parameters the PR name appears twice: "+name);
      }
      // System.out.println("Adding PR id: "+id);
      prids.put(id, pr);
    }
    // set the PR runtime parameters 
    for(String prId : prParms.keySet()) {
      String[] contrprname = prId.split("\t");
      if(contrprname[0].equals(cName)) {
        ProcessingResource pr = prids.get(prId);
        if(pr == null) {
          throw new GateRuntimeException("Cannot set PR parameter, no PR found with id: "+prId);
        }
        Map<String,Object> prparm = prParms.get(prId);
        for(String parmName : prparm.keySet()) {
          Object parmValue = prparm.get(parmName);
          try {
            pr.setParameterValue(parmName, parmValue);
          } catch (ResourceInstantiationException ex) {
            throw new GateRuntimeException("Could not set parameter "+parmName+" for PR id "+prId+" to value "+parmValue);
          }
        } // for parmName : prparm.keySet
      } // if controller names match
    }
  }
  }
}
