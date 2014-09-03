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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import static javax.swing.Action.SHORT_DESCRIPTION;
import javax.swing.JOptionPane;
import org.apache.log4j.Logger;

// NOTE: document how and when this applies the config settings!
// = java properties are set when the config file is read, but are 
//   NOT unset or restored (at the moment) when the config file is replaced
//   or cleared! Java properties only cen be set to a new value when (re)loading
//   a config file that explicitly has a setting for them
// = runtime parameters, run modes and document features are set whenever
//   execute() is done
// Interaction with Pipeline: runtime parameters and run modes adn document features
// are not set by the Pipeline PR if this Controller is used as a containing controller
// and the config file URL is identical. Otherwise the Pipeline PR sets all of those
// in addition.


/**
 *
 * @author johann
 */
@CreoleResource(name = "Parametrized Corpus Controller",
        comment = "A conditional corpus controller that can be parametrized from a config file",
        helpURL = "https://github.com/johann-petrak/gateplugin-modularpipelines/wiki/ParametrziedCorpusController")
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

  protected static final Logger logger = Logger
          .getLogger(ParametrizedCorpusController.class);
  
  
  @Override
  public Resource init() {
    config = Utils.readConfigFile(getConfigFileUrl());
    logger.debug("Config loaded for "+this.getName()+" config is "+config);
    return this;
  }

  @Override
  public void reInit() throws ResourceInstantiationException {
    init();
  }

  @Override
  public void execute() throws ExecutionException {
    Utils.setControllerParms(this, config);
    // if we do have a document and we do have document features to set,
    // do it now
    if(document != null && config.docFeatures != null && !config.docFeatures.isEmpty()) {
      logger.debug("DEBUG parametrized controller pipeline "+this.getName()+": setting document features "+config.docFeatures);
      document.getFeatures().putAll(config.docFeatures);
    } else {
      logger.debug("DEBUG parametrized controller pipeline "+this.getName()+": NOT setting document features");
    }
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
          if (getConfigFileUrl() != null) {
            config = Utils.readConfigFile(getConfigFileUrl());
            logger.info("Reloaded config file " + getConfigFileUrl());
          } else {
            logger.info("Nothing re-loaded, not config file set");
          }
        }
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
          CreoleRegister reg = Gate.getCreoleRegister();
          String resourceClass = "at.ofai.gate.modularpipelines.ParametrizedCorpusController";
          ResourceData rd = reg.get(resourceClass);
          if (rd == null) {
            JOptionPane.showMessageDialog(MainFrame.getInstance(),
                    "Error: could not find our own ParametrizedCorpusControllerClass!\n",
                    "GATE", JOptionPane.ERROR_MESSAGE);
            return;
          }
          NewResourceDialog nrd = new NewResourceDialog(MainFrame.getInstance(), "XXXXXXXXXXXXXXX", false);
          boolean ok = nrd.show(rd, "Change or remove config file");
          if (ok) {
            FeatureMap parms = nrd.getSelectedParameters();
            if (parms == null) {
              return;
            }
            URL newUrl;
            try {
              String urlString = (String)parms.get("configFileUrl");
              if(urlString == null) {
                newUrl = null;
              } else {
                newUrl = new URL((String)parms.get("configFileUrl"));
              }
            } catch (MalformedURLException ex) {
              logger.error("Got an exception",ex);
              return;
            }
            setConfigFileUrl(newUrl);
            if (newUrl != null) {
              config = Utils.readConfigFile(getConfigFileUrl());
              logger.info("Reloaded config file " + getConfigFileUrl());
            } else {
              config = new Config();
              logger.info("Cleared config data");
            }
          }
        }
      ;
    }
    );
      
      
    }
    return actions;
  }
}
