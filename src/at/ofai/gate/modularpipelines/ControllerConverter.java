
package at.ofai.gate.modularpipelines;

import gate.CreoleRegister;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;
import gate.ProcessingResource;
import gate.Resource;
import gate.creole.ConditionalSerialAnalyserController;
import gate.creole.ResourceData;
import gate.creole.ResourceInstantiationException;
import gate.creole.RunningStrategy;
import gate.creole.metadata.AutoInstance;
import gate.creole.metadata.CreoleResource;
import gate.gui.MainFrame;
import gate.gui.NameBearerHandle;
import gate.gui.NewResourceDialog;
import gate.gui.ResourceHelper;
import gate.persist.PersistenceException;
import gate.util.Err;
import gate.util.persistence.PersistenceManager;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import static javax.swing.Action.SHORT_DESCRIPTION;
import javax.swing.JOptionPane;

/**
 * Provide a right-click menu option for converting a Conditional Corpus 
 * Controller to our Parametrized Corpus Controller.
 * 
 * Since this tool gets initialized when the plugin is loaded, we also use
 * it to register the Persistence for the ParametrizedCorpusController
 * 
 * @author Johann Petrak
 */
@SuppressWarnings("serial")
@CreoleResource(
        name = "Controller converter", 
        tool = true,        
        autoinstances = @AutoInstance, 
        comment = "Convert an existing Conditional Corpus Controller to a Parametrized Corpus Controller", 
        helpURL = "https://github.com/johann-petrak/gateplugin-modularpipelines/wiki/Parametrized-Corpus-Controller")
public class ControllerConverter extends ResourceHelper {

  @Override
  public Resource init() throws ResourceInstantiationException {    
    try {
      PersistenceManager.registerPersistentEquivalent(
              at.ofai.gate.modularpipelines.ParametrizedCorpusController.class, 
              at.ofai.gate.modularpipelines.ParametrizedCorpusControllerPersistence.class);
    } catch(PersistenceException ex) {
      throw new ResourceInstantiationException("Could not register persistence",ex);
    }
    return this;
  }
  
  MakeParametrizedCorpusControllerAction action;
  @Override
  protected List<Action> buildActions(NameBearerHandle nbh) {
    List<Action> actions = new ArrayList<Action>();
    if(nbh.getTarget() instanceof ConditionalSerialAnalyserController && 
       !(nbh.getTarget() instanceof ParametrizedCorpusController)) {
      action = new MakeParametrizedCorpusControllerAction((ConditionalSerialAnalyserController)nbh.getTarget());
      actions.add(action);
    }
    return actions;
  }
  
  class MakeParametrizedCorpusControllerAction extends AbstractAction {
    private static final long serialVersionUID = 1L;
    public ConditionalSerialAnalyserController target;

    public MakeParametrizedCorpusControllerAction(ConditionalSerialAnalyserController nb) {      
      super("Convert to Parametrized Corpus Controller");
      putValue(SHORT_DESCRIPTION, "Convert to Parametrized Corpus Controller");
      target = nb;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      ConditionalSerialAnalyserController existingController = target;
      try {
        FeatureMap parms;
        // TODO: prompt for config file
        // get the register
        CreoleRegister reg = Gate.getCreoleRegister();
        // now try to get the entry for our own Parametrized corpus controller
        String resourceClass = "at.ofai.gate.modularpipelines.ParametrizedCorpusController";
        ResourceData rd = reg.get(resourceClass);
        if(rd == null) {
          JOptionPane.showMessageDialog(MainFrame.getInstance(),
              "Error: could not find our own ParametrizedCorpusControllerClass!\n" , "GATE", JOptionPane.ERROR_MESSAGE);          
        } else {
          NewResourceDialog nrd = new NewResourceDialog(MainFrame.getInstance(), "Resource parameters", true);
          boolean ok = nrd.show(rd,"Convert to a Parametrized Corpus Controller");
          if(ok) {
            parms = nrd.getSelectedParameters();
            String newName = nrd.getResourceName();
            ParametrizedCorpusController newController =
              (ParametrizedCorpusController)Factory
                .createResource(resourceClass,parms);
            newController.getFeatures().putAll(existingController.getFeatures());
            newController.setName(existingController.getName());
            Iterator<?> itp = existingController.getPRs().iterator();
            while(itp.hasNext()) {
              newController.add((ProcessingResource)itp.next());
            }
            List<RunningStrategy> runstrats = existingController.getRunningStrategies();
            newController.setRunningStrategies(runstrats);
            List<ProcessingResource> emptyPrs = Collections.emptyList();
            existingController.setPRs(emptyPrs);
            List<RunningStrategy> emptyStrats = Collections.emptyList();
            existingController.setRunningStrategies(emptyStrats);
            Factory.deleteResource(existingController);
            if(newName != null && !newName.isEmpty()) {
              newController.setName(newName);
            }
          }
        }
      } catch(Exception ex) {
        JOptionPane.showMessageDialog(MainFrame.getInstance(),
            "Error!\n" + ex.toString(), "GATE", JOptionPane.ERROR_MESSAGE);
        ex.printStackTrace(Err.getPrintWriter());
      }
    }
  }
  
  
}
