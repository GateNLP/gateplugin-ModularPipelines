package at.ofai.gate.modularpipelines;

import gate.FeatureMap;
import gate.creole.ResourceInstantiationException;
import gate.persist.PersistenceException;
import gate.util.persistence.ConditionalSerialAnalyserControllerPersistence;
import gate.util.persistence.PersistenceManager;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 *
 * @author Johann Petrak
 */
public class ParametrizedCorpusControllerPersistence extends  ConditionalSerialAnalyserControllerPersistence {
  // we only override the createObject method because we need to intercept
  // the creation of the init params
  
  protected static final Logger logger = Logger.getLogger(ParametrizedCorpusControllerPersistence.class);
  
  @Override
  public Object createObject() throws PersistenceException, ResourceInstantiationException {
    initParams = PersistenceManager.getTransientRepresentation(
            initParams,containingControllerName,initParamOverrides);
    FeatureMap ourParms = (FeatureMap)initParams;
    logger.debug("=== Persistence START: "+ourParms);
    // NOTE: in order to be able for a parent pipeline config file to override
    // the config settings of a sub pipeline, INCLUDING the init time settings,
    // we would need to be able to somehow now here at this point what the
    // parent pipeline setting is. However, I cannot see any reasonably simple
    // mechanism that would allow us to do this, so we need to stick with 
    // the general strategy of using the global system property for this.
    // Unfortunately that strategy will affect all pipelines in the whole VM!
    URL theURL = (URL)ourParms.get("configFileUrl");
    Config config = Utils.readConfigFile(theURL);    
    // if we could read the config file, set the parameter override map
    // At this point we should have any config from either the config file 
    // configFileUrl or the file specified by the overriding system property. 
    if(config != null && config.prInitParms != null) {
      if(initParamOverrides == null) {
        initParamOverrides = new HashMap<String,Map<String,Object>>();
      }
      initParamOverrides.putAll(config.prInitParms);
    }
    // all the rest can be handled by the ConditionalSerialAbnalyserControllerPersistence,
    // but we should get a ParametrizedCorpusController instance.

    // NOTE: This will eventually bubble up to the createObject() method for the Resource
    // which in turns Factory.create which in turns calls the new resource's init
    // method. However, not everythin will be in place at that point because the
    // createObject method for the controller will only deserialize the PRs after
    // the resource has been created. 
    ParametrizedCorpusController obj = (ParametrizedCorpusController)super.createObject();
    // here we should not only have the init parameters but the object should actually 
    // have been created and initialized (our own init method has been called, but
    // only with a partly initialized object, which did not yet have the PR list.
    // To run any initialization which must happen after we have everything, we
    // use our own afterLoadCompleted() method:
    logger.debug("=== Persistence END: "+ourParms+" calling afterLoadCompleted");
    obj.afterLoadCompleted();
    return obj;
  }
}
