package at.ofai.gate.modularpipelines;

import gate.Controller;
import gate.GateConstants;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.gui.ActionsPublisher;
import gate.gui.MainFrame;
import java.awt.event.ActionEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import static javax.swing.Action.SHORT_DESCRIPTION;

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
  @RunTime
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
  protected URL oldConfigFileUrl = null;
  
  @Override
  public Resource init() throws ResourceInstantiationException {
    config = Utils.readConfigFile(getConfigFileUrl());
    oldConfigFileUrl = configFileUrl;
    return this;
  }
  protected Config config;

  protected void setControllerParms(Controller cntrlr) {
    Utils.setControllerParms(cntrlr, config);
  }
  
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
              if(getConfigFileUrl() != null) {
                try {                
                  MainFrame.lockGUI("Reading config file "+getConfigFileUrl()+"...");
                  config = Utils.readConfigFile(getConfigFileUrl());
                  oldConfigFileUrl = configFileUrl;
                } finally {
                  MainFrame.unlockGUI();
                }
                System.out.println("Reloaded config file "+getConfigFileUrl());
              } else {
                System.out.println("Nothing re-loaded, not config file set");
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
