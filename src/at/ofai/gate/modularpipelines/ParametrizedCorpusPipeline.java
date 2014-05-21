/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ofai.gate.modularpipelines;

import gate.GateConstants;
import gate.Resource;
import gate.creole.ConditionalSerialAnalyserController;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.gui.ActionsPublisher;
import gate.gui.MainFrame;
import java.awt.event.ActionEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import static javax.swing.Action.SHORT_DESCRIPTION;

/**
 *
 * @author johann
 */
@CreoleResource(name = "Parametrized Corpus Pipeline",
        comment = "A conditional corpus controller that can be parametrized from a config file",
        helpURL="https://github.com/johann-petrak/gateplugin-modularpipelines/wiki/Pipline-PR")
public class ParametrizedCorpusPipeline extends ConditionalSerialAnalyserController
  implements ActionsPublisher {
  
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

  Config config;
  
  @Override public Resource init() {
    config = Utils.readConfigFile(getConfigFileUrl());
    return this;
  }
  
  @Override
  public void reInit() throws ResourceInstantiationException {
    init();
  }  
  
  @Override
  public void execute() throws ExecutionException {
    Utils.setControllerParms(this, config);
    super.execute();
  }
  
  private List<Action> actions;
  
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
                config = Utils.readConfigFile(getConfigFileUrl());
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