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
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


public class SetParmsAndFeatsFromConfigBase extends AbstractLanguageAnalyser {

  
  // For PRs, the property file must contain settings in the following form:
  // prparm.settingid.name = "name of the PR in the controller"
  // prparm.settingid.parm = "name of the parameter in the controller"
  // prparm.settingid.value = "value to set the parameter to"
  // prparm.settingid.controller = "name of the controller" 
  // anythitng that is valid as a prefix of a property name can be used instead
  // of "pr1"
  // For document features:
  // docfeature.somefeaturenametoset = "the value"
  // All properties which do not start with a recognized prefix (at the moment,
  // prparm or docfeature) are added to the system properties.
  
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
  protected Map<String,Map<String,String>> prParms = null;
  
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
      prParms = new HashMap<String,Map<String,String>>();
      
      // check all the keys in the property file and see if they match the
      // name pattern for one of the things we already support
      for(String key : properties.stringPropertyNames()) {
        if(key.startsWith("docfeature.")) {
          docFeaturesFromConfig.put(key.replaceAll("^docfeature\\.", ""), properties.getProperty(key));
        } else if(key.startsWith("prparm.")) {
          String settingId = key.substring("prparm.".length());
          settingId = settingId.replaceAll("\\.[^.]+$", "");
          String nameProp = "prparm."+settingId+".name";
          String parmProp = "prparm."+settingId+".parm";
          String valueProp = "prparm."+settingId+".value";
          String contrProp = "prparm."+settingId+".controller";
          String prName = properties.getProperty(nameProp,null);
          String prParm = properties.getProperty(parmProp,null);
          String prValue = properties.getProperty(valueProp,null);
          String prContr = properties.getProperty(contrProp,null);
          if(prName == null) {
            throw new GateRuntimeException("No property specifying pr name (.name) for pr parameter setting "+key);
          }
          if(prParm == null) {
            throw new GateRuntimeException("No property specifying  parameter name (.parm) for pr parameter setting "+key);
          }
          if(prValue == null) {
            throw new GateRuntimeException("No property specifying parameter value (.value) for pr parameter setting "+key);
          }
          if(prContr == null) {
            throw new GateRuntimeException("No property specifying controller name (.controller) for pr parameter setting "+key);
          }
          String prId = prContr+"\t"+prName;
          Map<String,String> prparm = prParms.get(prId);
          if(prparm == null) {
            prparm = new HashMap<String,String>();
          }
          prparm.put(prParm, prValue);
          prParms.put(prId,prparm);
        } else { // startswith prparm
          System.getProperties().put(key, properties.getProperty(key));
        }
      }
      } else if(configFile.toString().endsWith(".properties")) {
        // TBD!
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
        Map<String,String> prparm = prParms.get(prId);
        for(String parmName : prparm.keySet()) {
          String parmValue = prparm.get(parmName);
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
