package at.ofai.gate.modularpipelines;

import gate.CreoleRegister;
import gate.Document;
import gate.FeatureMap;
import gate.Gate;
import gate.GateConstants;
import gate.LanguageAnalyser;
import gate.ProcessingResource;
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


// NOTE: ok, implementing our own controller which depends on proper initialization
// is a nightmare: the problem is that init() is being called add odd and different
// times depending on how the controller is initialized: if we read from a gapp
// file, then init is called *before* anything is initilized by the system
// other than the bare resource part. But if we custom duplicate, then the 
// instance is already initialized. Finally if a user creates the instance
// with the Factory, we will get an un-initialized object but afterLoadCompleted
// will not get called (our own method to signal that loading has been completed). 

/**
 *
 * @author johann
 */
@CreoleResource(name = "Parametrized Corpus Controller",
        comment = "A conditional corpus controller that can be parametrized from a config file",
        helpURL = "https://github.com/johann-petrak/gateplugin-modularpipelines/wiki/ParametrziedCorpusController")
public class ParametrizedCorpusController extends ConditionalSerialAnalyserController
        implements ActionsPublisher {

  private static final long serialVersionUID = 5865826552244553897L;

  @Optional
  @CreoleParameter(
          comment = "The URL of the config file for setting parameters and features (.properties or .yaml)",
          suffixes = "properties;yaml")
  public void setConfigFileUrl(URL fileUrl) {
    logger.debug("Controller "+this.getName()+" Setting config file URL to "+fileUrl);
    if(weAreInitialized) {
      if(config.origUrl != null && fileUrl == null) {
        logger.debug("Controller: create empty config in set");
        config = new Config();
      } else if(config.origUrl == null && fileUrl != null) {
        logger.debug("Controller: read config in set1 "+fileUrl);
        config = Utils.readConfigFile(fileUrl);      
      } else if(config.origUrl != null && !config.origUrl.equals(fileUrl)) {
        logger.debug("Controller: read config in set2 "+fileUrl);
        config = Utils.readConfigFile(fileUrl);      
      } else {
        logger.debug("doing nothing config.origUrl="+config.origUrl+" fileUrl="+fileUrl);       
      }
    } else {
      logger.debug("Controller "+this.getName()+" not fully initialized yet, not reloading config");
    }
    // if the config contains a "inheritconfig" setting, i.e. if the global
    // config Url field is not null, we need to set it for all direct
    // sub-pipelines.
    // However, if this is called *before* this ParametrizedCorpusController
    // instance got initialized, initialization will happen and there we
    // do the setting for the sub-pipelines, so we will only set it here
    // if initialization has not happend yet.
    // The difficulty arises since this "init" parameter can actually also
    // be updated after initialization.
    if(weAreInitialized && config.globalConfigFileUrl != null) {
      logger.debug("!!!!! Controller/setConfigFileUrl: "+this.getName()+" set config for sub controllers to "+config.globalConfigFileUrl);
      setConfigForSubControllers(config.globalConfigFileUrl);
    }
    
    configFileUrl = fileUrl;
  }

  public URL getConfigFileUrl() {
    return configFileUrl;
  }
  protected URL configFileUrl = null;
  Config config = new Config();

  protected static final Logger logger = Logger
          .getLogger(ParametrizedCorpusController.class);
  
  private boolean weAreInitialized = false;
  
  /**
   * Do the necessary initialization.
   * 
   * This method gets called when the resource of this type has been created
   * by the Factory, using the init parameters present at creation time.
   * However, when a controller is read from a file, this method gets called 
   * before the contained PRs and other elements are de-serialized. 
   * Our own Persistence class therefore also invokes the afterLoadCompleted()
   * method after the object has been fully loaded.
   * 
   * @return
   * @throws ResourceInstantiationException 
   */
  @Override
  public Resource init() throws ResourceInstantiationException {    
    config = Utils.readConfigFile(getConfigFileUrl()); 
    return this;
  }
  
  public void afterLoadCompleted() {    
    logger.debug("****** Controller: "+this.getName()+" read config in afterLoadCompleted "+getConfigFileUrl()+" config="+config);
    //logger.debug("Config loaded for "+this.getName()+" config is "+config);
    // If the config file we just read contains the "inheritconfig" setting,
    // try to set the config file of all directly included sub-pipelines to 
    // the same config file. 
    // When this init() method is executed, all the subpipelines already have
    // been loaded and their config files were read, so re-setting their 
    // config files now will not change any init parameters. 
    if(config.globalConfigFileUrl != null) {
      logger.debug("Controller/afterLoadCompleted: "+this.getName()+" set config for sub controllers to "+config.globalConfigFileUrl);
      setConfigForSubControllers(config.globalConfigFileUrl);
    }    
    weAreInitialized = true;
  }

  @Override
  public void reInit() throws ResourceInstantiationException {
    init();
  }

  /**
   * Run the controller on a corpus or for one document.
   * 
   * This method gets invoked on the top level once for the whole corpus,
   * in which case the document will be null, and for embedded pipelines once
   * for each document, in which case the document will be non-null. 
   * 
   * @throws ExecutionException 
   */
  @Override
  public void execute() throws ExecutionException {
    logger.debug("Running execute() for "+this.getName()+" config is "+config);
    // We set the rutime parameters and run modes here, which means once 
    // for the whole corpus for the main pipeline but for each document 
    // for the sub-pipelines. Nothing we can do about this with the 
    // current design of controllers.
    Utils.setControllerParms(this, config);
    // NOTE: the document features will always be set in the runComponent
    // callback.
    /*
    if(document != null) {
      if(config.docFeatures != null && !config.docFeatures.isEmpty()) {
        logger.debug("DEBUG parametrized controller pipeline "+this.getName()+"/execute: setting document features "+config.docFeatures);
        Utils.setDocumentFeatures(document.getFeatures(), config);
      } else {
        logger.debug("DEBUG parametrized controller pipeline "+this.getName()+"/execute: NOT setting document features, document="+document+" config="+config);
      }
    }
    */
    // This will eventually delegate to the super implementation fo 
    // executeImpl which will then eventually delegate to runComponent, which
    // we handle separately below.
    super.execute();
  }

  /**
   * If a controller is run on a whole corpus, this method will get called
   * for each component and each document and each component will have its
   * document parameter set to the document to process. 
   * 
   * This is always called for *all* components in the controller, if the 
   * controller is a conditional controller, then the running strategy
   * is only checked once we delegate up to the super... implementation of 
   * runComponent which eventually actually executes the PR, if necessary.
   * Since we always get called for all components, we always process only
   * for the first, which is componentIndex=0. 
   * 
   * @param componentIndex
   * @throws ExecutionException 
   */
  @Override
  protected void runComponent(int componentIndex) throws ExecutionException{
    logger.debug("Running "+this.getName()+"/runComponent "+componentIndex);    
    if(componentIndex == 0) {
      Document doc = ((LanguageAnalyser)prList.get(componentIndex)).getDocument();
      if(doc != null && config.docFeatures != null && !config.docFeatures.isEmpty()) {
        logger.debug("DEBUG parametrized controller pipeline "+this.getName()+"/runComponent: setting document features "+config.docFeatures);
        Utils.setDocumentFeatures(doc.getFeatures(), config);
      } else {
        logger.debug("DEBUG parametrized controller pipeline "+this.getName()+"/runComponent: NOT setting document features, document="+doc+" config="+config);
      }
    } else {
      logger.debug("DEBUG  parametrized controller pipeline "+this.getName()+"/runComponent: set document features already done");
    }
    
    // MAYBE TODO: here we should also deal with any special document features
    // which should be used to set other things in the pipeline.
    // This would make it possible for the webservice to influence the 
    // config settings on a doc by doc basis easily.
    // The way to do this should probably be:
    // = process all features which start with a certain prefix
    // = update the config datastructure based on these values. Not sure yet
    //   how, probably by structured names??
    
    // now delegate to the correct super implementation of runComponent 
    // which will eventually decide if to run the PR and then run it.
    super.runComponent(componentIndex);    
  }
  
  public void setConfigForSubControllers(URL configFileUrl) {
    logger.debug("Running setConfigForSubControllers in "+this.getName()+" config="+configFileUrl+" have components: "+prList);
    for (int componentIndex = 0; componentIndex < prList.size(); componentIndex++) {
      ProcessingResource pr = prList.get(componentIndex);
      if (pr instanceof ParametrizedCorpusController) {
        logger.debug("Setting config file for embedded pipeline " + pr.getName());
        ((ParametrizedCorpusController) pr).setConfigFileUrl(config.globalConfigFileUrl);
      } else if (pr instanceof Pipeline) {
        logger.debug("From controller "+this.getName()+" Setting config file for PipelinePR " + pr.getName());
        ((Pipeline) pr).setConfig4Pipeline(config.globalConfigFileUrl);
      }
    }
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
            logger.debug("Reloaded config file " + getConfigFileUrl());
          } else {
            logger.debug("Nothing re-loaded, not config file set");
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
              logger.debug("Reloaded config file " + getConfigFileUrl());
            } else {
              config = new Config();
              logger.debug("Cleared config data");
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
