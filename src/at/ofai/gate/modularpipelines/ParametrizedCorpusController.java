/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ofai.gate.modularpipelines;

import gate.CreoleRegister;
import gate.FeatureMap;
import gate.Gate;
import gate.GateConstants;
import gate.Resource;
import gate.creole.ConditionalSerialAnalyserController;
import gate.creole.ExecutionException;
import gate.creole.ResourceData;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.gui.ActionsPublisher;
import gate.gui.MainFrame;
import gate.gui.NewResourceDialog;
import java.awt.event.ActionEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import static javax.swing.Action.SHORT_DESCRIPTION;
import javax.swing.JOptionPane;

/**
 *
 * @author johann
 */
@CreoleResource(name = "Parametrized Corpus Controller",
        comment = "A conditional corpus controller that can be parametrized from a config file",
        helpURL="https://github.com/johann-petrak/gateplugin-modularpipelines/wiki/ParametrziedCorpusController")
public class ParametrizedCorpusController extends ConditionalSerialAnalyserController
  implements ActionsPublisher {
  
  private static final long serialVersionUID = 5865826533344553897L;  
  
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
      
      // Action 1: re-load the config file
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
      
      // Action 2: change the config file
      actions.add(
              new AbstractAction("Change/remove config file (no effect on init parms)") {
        {
          putValue(SHORT_DESCRIPTION,
                  "Set or change the config, or remove it completely (no effect on init parms)");
          putValue(GateConstants.MENU_PATH_KEY,
                  new String[]{"WTFISTHIS??????"});
        }
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent evt) {
          Runnable runnableAction = new Runnable() {
            @Override
            public void run() {
              CreoleRegister reg = Gate.getCreoleRegister();
              String resourceClass = "at.ofai.gate.modularpipelines.FakeResource";
              ResourceData rd = reg.get(resourceClass);
              if(rd == null) {
                JOptionPane.showMessageDialog(MainFrame.getInstance(),
                "Error: could not find our own ParametrizedCorpusControllerClass!\n",
                "GATE", JOptionPane.ERROR_MESSAGE);          
                return;
              }
              NewResourceDialog nrd = new NewResourceDialog(MainFrame.getInstance(), "XXXXXXXXXXXXXXX", false);
              boolean ok = nrd.show(rd,"Change or remove config file");
              if(ok) {
                FeatureMap parms = nrd.getSelectedParameters();
                if(parms==null) return;
                URL newUrl = (URL)parms.get("configFileUrl");
                setConfigFileUrl(newUrl);
                if (newUrl != null) {
                  try {
                    MainFrame.lockGUI("Reading config file " + getConfigFileUrl() + "...");
                    config = Utils.readConfigFile(getConfigFileUrl());
                  } finally {
                    MainFrame.unlockGUI();
                  }
                  System.out.println("Reloaded config file " + getConfigFileUrl());
                } else {
                  config = new Config();
                  System.out.println("Cleared config data");
                }
              }
            }
          };
          Thread thread = new Thread(runnableAction, "ModularPipelinesConfigurationChanger");
          thread.start();
        };
      });
      
      
    }
    return actions;
  }
  
  
  
}
