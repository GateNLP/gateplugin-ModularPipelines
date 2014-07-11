package at.ofai.gate.modularpipelines;

import gate.FeatureMap;
import gate.creole.ResourceInstantiationException;
import gate.persist.PersistenceException;
import gate.util.persistence.ConditionalSerialAnalyserControllerPersistence;
import gate.util.persistence.PersistenceManager;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Johann Petrak
 */
public class ParametrizedCorpusControllerPersistence extends  ConditionalSerialAnalyserControllerPersistence {
  // we only override the createObject method because we need to intercept
  // the creation of the init params
  @Override
  public Object createObject() throws PersistenceException, ResourceInstantiationException {
    initParams = PersistenceManager.getTransientRepresentation(
            initParams,containingControllerName,initParamOverrides);
    FeatureMap ourParms = (FeatureMap)initParams;
    URL theURL = (URL)ourParms.get("configFileUrl");
    Config config = Utils.readConfigFile(theURL);
    // if we could read the config file, set the parameter override map
    if(config != null && config.prInitParms != null) {
      if(initParamOverrides == null) {
        initParamOverrides = new HashMap<String,Map<String,Object>>();
      }
      initParamOverrides.putAll(config.prInitParms);
    }
    Object obj = super.createObject();
    // here we should not only have the init parameters but the object should actually 
    // have been created and initialized, so we should have 
    return obj;
  }
}
