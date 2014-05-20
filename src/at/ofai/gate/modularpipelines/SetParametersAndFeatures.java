package at.ofai.gate.modularpipelines;

import gate.Controller;
import gate.Resource;
import gate.creole.ControllerAwarePR;
import gate.creole.ExecutionException;
import gate.creole.metadata.CreoleResource;
import gate.util.GateRuntimeException;

// TODO: support other formats for the confiug file? 


/**
 * Set parameters and features from a config file.
 * This PR can be used to set document features and PR parameters from the
 * settings in a property file. 
 * 
 * @author Johann Petrak
 */
@CreoleResource(name = "SetParametersAndFeatures",
        comment = "Set parameters and features of controller, corpus, PR etc from property files",
        helpURL="http://code.google.com/p/gateplugin-modularpipelines/wiki/SetParametersAndFeatures")

// This is a PR which lets the user, for each kind of setting, specify a property
// file which will set the features or parameters from that property file.


public class SetParametersAndFeatures extends SetParmsAndFeatsFromConfigBase 
  implements ControllerAwarePR {

  
  
  @Override
  public Resource init() {
    readConfigFile();
    return this;
  }
  
  protected Controller controller = null;
  
  @Override
  public void execute() {
    if(controller != null) {
      setControllerParms(controller);
    } else {
      throw new GateRuntimeException("Something is very wrong: controller is null!");
    }
    if(getDocument() != null) {
      getDocument().getFeatures().putAll(docFeaturesFromConfig);
    }
    // TODO: maybe: restore parameters?
  }

  @Override
  public void controllerExecutionStarted(Controller cntrlr) throws ExecutionException {
    controller = cntrlr;
  }

  @Override
  public void controllerExecutionFinished(Controller cntrlr) throws ExecutionException {
    
  }

  @Override
  public void controllerExecutionAborted(Controller cntrlr, Throwable thrwbl) throws ExecutionException {
    
  }

  
}
