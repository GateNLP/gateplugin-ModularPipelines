/*
 *  Pipeline.java
 *
 *
 */

package at.ofai.gate.modularpipelines;

import gate.Controller;
import gate.CorpusController;
import gate.Factory;
import gate.Factory.DuplicationContext;
import gate.FeatureMap;
import gate.LanguageAnalyser;
import gate.ProcessingResource;
import gate.Resource;
import gate.creole.ConditionalSerialAnalyserController;
import gate.creole.ControllerAwarePR;
import gate.creole.CustomDuplication;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.SerialAnalyserController;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.persist.PersistenceException;
import gate.util.GateRuntimeException;
import gate.util.persistence.PersistenceManager;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.log4j.Logger;

// TODO: save/restore parameter values for both config and parm settings
// TODO: add debug setting to prevent restoring the parameter values and
//       enable some debug output
// TODO: add feature to delete document feature: deletedocfeature

/** 
 * A processing resource that wraps a controller loaded from a pipeline file.
 * This makes it possible to create modular pipelines which contain sub-pipelines
 * represented by this PR. The advantage over conventional nested pipelines
 * is that pipelines wrapped by this PR always represent the newest version
 * of the original pipeline file when they are loaded or re-initialized.
 * Re-initializing this PR will recursively delete all resources loaded by
 * the pipeline and reload a fresh copy of the pipeline from its pipeline file.
 * 
 * @author Johann Petrak
 */
@CreoleResource(name = "Pipeline",
        comment = "Represents a pipeline or corpus pipeline loaded from a xgapp/gapp file",
        helpURL="https://github.com/johann-petrak/gateplugin-modularpipelines/wiki/Pipline-PR")
public class Pipeline  extends SetParmsAndFeatsFromConfigBase
  implements ProcessingResource, CustomDuplication, ControllerAwarePR {
  private static final long serialVersionUID = 1L;

  @CreoleParameter(comment="The URL of the saved pipeline file")
  public void setPipelineFileURL(URL fileURL) {
    pipelineFileURL = fileURL;
  }
  public URL getPipelineFileURL() {
    return pipelineFileURL;
  }
  protected URL pipelineFileURL = null;
  
  // the parameter overwrites will be done before the "enclosed" pipeline is run and will
  // be undone afterwards (the old values are saved). This can only affect runtime parameters!
  // TODO: what happens when somebody tries to change an init parm that way?
  @Optional
  @RunTime
  @CreoleParameter(comment="Parameters to overwrite before running a pipeline. In the form PrName.ParmName/value. ")
  public void setPipelineParameters(FeatureMap parameters) {
    pipelineParameters = parameters;
  }
  public FeatureMap getPipelineParameters() {
    return pipelineParameters;
  }
  protected FeatureMap pipelineParameters;
      
  protected Controller controller;
  protected static final Logger log = Logger
          .getLogger(Pipeline.class);
  
  
  @Override
  public Resource init() throws ResourceInstantiationException {
    if(getPipelineFileURL() == null) {
      throw new ResourceInstantiationException("pipelineFileURL must be set");
    }
    try {
      // TODO: not sure how the controller can ever be non-null in init()
      // therefore, we add some debugging code here ...
      if(controller == null) {
        initialise_pipeline();
      } else {
        System.out.println("ModularPipelines DEBUG: controller is not null in init(): "+controller.getName());
      }
    } catch (Exception ex) {
      throw new ResourceInstantiationException(
        "Could not load pipeline "+getPipelineFileURL(),ex);
    }
    super.init();
    return this;
  }
  
  @Override
  public void reInit() {
    Factory.deleteResource(controller);
    try {
      controller = null;
      initialise_pipeline();
    } catch (Exception ex) {
      throw new GateRuntimeException(
        "Could not re-load pipeline "+getPipelineFileURL(),ex);
    }
  }
  
  @Override
  public void interrupt() {
    controller.interrupt();
  }
  
  @Override
  public void execute() {
    // invoking a corpus controller will only work if the corpus is set,
    // even when the corpus is not used in a recursive invocation 
    // (if a corpus controller is invoked inside a corpus controller, the
    // document is set and the inner controller is only run on that single
    // document while the corpus is ignored).
    if(oldConfigFileUrl != configFileUrl) {
      config = Utils.readConfigFile(configFileUrl);
      oldConfigFileUrl = configFileUrl;
    }
    
    if(controller instanceof CorpusController) {      
      ((CorpusController)controller).setCorpus(corpus);      
    }
    if(controller instanceof LanguageAnalyser) {      
      ((LanguageAnalyser)controller).setDocument(document);      
    }
    try {
      log.debug(("Running pipeline "+controller.getName()+" on "+
              (document != null ? document.getName() : "(no document)" )));

      HashMap<String,Object> savedParms = new HashMap<String,Object>();
      HashMap<String,Resource> name2pr = new HashMap<String,Resource>();

      // if we want to override parameters: these can come from the pipelineParameters
      // parameter (our own runtime parameter which is a feature map) 
      if(pipelineParameters != null && !pipelineParameters.isEmpty()) {
        // get all the prs and create a map of names->prs
        ArrayList<Resource> prlist = new ArrayList<Resource>();
        prlist.addAll(controller.getPRs());
        for(Resource pr : prlist) {
          name2pr.put(pr.getName(), pr);
        }
          
        // process each parameter in the featuremap
        for(Object keyObject : pipelineParameters.keySet()) {
          String key = (String)keyObject;
          String[] prparm = key.split("->",2);
          if(prparm.length != 2) {
            throw new GateRuntimeException("Not a correct pipeline parameter key (must be prname->prparm): "+key);
          }
          String prname = prparm[0];
          String parmname = prparm[1]; 
          // try to find the PR
          Resource pr = name2pr.get(prparm[0]);
          if(pr == null) {
            System.err.println("Could not set parameter "+parmname+" for PR "+prname);
          } else {
            // get the old parameter value
            boolean parmSaved = false;
            try {
              savedParms.put(key, pr.getParameterValue(parmname));
              parmSaved = true;
            } catch (ResourceInstantiationException e) {
              // TODO Auto-generated catch block
              System.err.println("Parameter not set, got an exception trying to save the parameter value for "+key);
              e.printStackTrace(System.err);
            }
            // if we could save the value, try to set the new value
            if(parmSaved) {
              try {
                Object value = pipelineParameters.get(keyObject);
                
                if(value instanceof String) {
                  String string = (String)value;
                  string = gate.Utils.replaceVariablesInString(string, corpus.getFeatures(), controller.getFeatures(),this.getFeatures());
                  value = string;
                }
                //System.err.println("Trying to set parameter "+parmname+" to value "+value+" for "+pr.getName());
                pr.setParameterValue(parmname, value);
              } catch (ResourceInstantiationException e) {
                System.err.println("Got an exception trying to set the new value for "+key);
                e.printStackTrace(System.err);
              }
            }
          }
          
        } // for
      } // if we have parameter overrides
      // now override any parameters from configured from the properties file
      //System.out.println("Trying to set controller parms for "+controller.getName());
      // TODO: we want to avoid setting stuff twice if the pipeline which we
      // run is parametrized and with the same config file. however, just 
      // comparing the URIs will not always work correctly if we have two
      // diferent URIs pointing to the same file because of symbolic links
      // or similar. We should use a different method to compare the configs
      // (e.g. hash-code of content)
      if(controller instanceof ParametrizedCorpusController && 
         isEqual(((ParametrizedCorpusController)controller).getConfigFileUrl(),getConfigFileUrl())) {
        //System.out.println("DEBUG: Pipeline: not setting parms because the pipeline is parametrized!");
      } else {
        setControllerParms(controller);
      }
      // finally set the document features
      if(document != null && config.docFeatures != null) {
        document.getFeatures().putAll(config.docFeatures);
      }
      controller.execute();
      // TODO: maybe: restore the parameters changed in setControllerParms?
      // if we have overriden some parameters, restore them
      if(!savedParms.isEmpty()) {
        for(String key : savedParms.keySet()) {
          String[] prparm = key.split("->",2);
          String prname = prparm[0];
          String parmname = prparm[1]; 
          // try to find the PR
          Resource pr = name2pr.get(prparm[0]);
          if(pr != null) {
            try {
              pr.setParameterValue(parmname, savedParms.get(key));
            } catch (ResourceInstantiationException e) {
              System.err.println("Could not restore value for "+key);
              e.printStackTrace(System.err);
            }
          }
        }
      } // if we need to restore parameters
    } catch (ExecutionException ex) {
      throw new GateRuntimeException(
        "Error executing pipeline "+pipelineFileURL,ex);
    } finally {
      if(controller instanceof LanguageAnalyser) {      
        ((LanguageAnalyser)controller).setDocument(null);      
      }
    }
  }
  
  boolean isEqual(Object one, Object two) {
    if(one == null && two == null) {
      return true;
    } else if(one == null) {
      return false;      
    } else if(two == null) {
      return false;
    } else {
      return one.equals(two);
    }
  }
  
  @Override
  public void cleanup() {
    Factory.deleteResource(controller);
  }
  
  
  protected void initialise_pipeline() throws PersistenceException,
    IOException, ResourceInstantiationException {
    log.debug("(Re-)initialising pipeline "+pipelineFileURL);
    controller = (Controller)PersistenceManager.loadObjectFromUrl(pipelineFileURL);
  }
  
  @Override
  public Resource duplicate(DuplicationContext ctx)
      throws ResourceInstantiationException {
    FeatureMap params = Factory.duplicate(getInitParameterValues(), ctx);
    params.putAll(Factory.duplicate(getRuntimeParameterValues(), ctx));
    FeatureMap features = Factory.duplicate(this.getFeatures(), ctx);
    
    Controller c = (Controller)Factory.duplicate(this.controller, ctx);
    params.put("controller", c);

    return Factory.createResource(this.getClass().getName(), params, features, this.getName());
  }
  @Override
  public void controllerExecutionStarted(Controller c)
      throws ExecutionException {
    if(controller instanceof ControllerAwarePR) {
      if(controller instanceof ConditionalSerialAnalyserController) {
        ((ConditionalSerialAnalyserController)controller).setCorpus(corpus);
      } else if(controller instanceof SerialAnalyserController) {
        ((SerialAnalyserController)controller).setCorpus(corpus);
      }
      ((ControllerAwarePR)controller).controllerExecutionStarted(c);
    }    
  }
  @Override
  public void controllerExecutionFinished(Controller c)
      throws ExecutionException {
    if(controller instanceof ControllerAwarePR) {
      if(controller instanceof ConditionalSerialAnalyserController) {
        ((ConditionalSerialAnalyserController)controller).setCorpus(corpus);
      } else if(controller instanceof SerialAnalyserController) {
        ((SerialAnalyserController)controller).setCorpus(corpus);
      }
      ((ControllerAwarePR)controller).controllerExecutionFinished(c);
      if(controller instanceof ConditionalSerialAnalyserController) {
        ((ConditionalSerialAnalyserController)controller).setCorpus(null);
      } else if(controller instanceof SerialAnalyserController) {
        ((SerialAnalyserController)controller).setCorpus(null);
      }
    }
  }
  @Override
  public void controllerExecutionAborted(Controller c, Throwable t)
      throws ExecutionException {
    if(controller instanceof ControllerAwarePR) {
      if(controller instanceof ConditionalSerialAnalyserController) {
        ((ConditionalSerialAnalyserController)controller).setCorpus(corpus);
      } else if(controller instanceof SerialAnalyserController) {
        ((SerialAnalyserController)controller).setCorpus(corpus);
      }
      ((ControllerAwarePR)controller).controllerExecutionAborted(c, t);
      if(controller instanceof ConditionalSerialAnalyserController) {
        ((ConditionalSerialAnalyserController)controller).setCorpus(null);
      } else if(controller instanceof SerialAnalyserController) {
        ((SerialAnalyserController)controller).setCorpus(null);
      }
    }    
  }
  
  
} // class Pipeline
