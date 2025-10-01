package gov.usgs.owi.nldi.dao;

import gov.usgs.owi.nldi.controllers.BaseController;
import gov.usgs.owi.nldi.model.FeatureList;
import gov.usgs.owi.nldi.resulthandler.FeatureListResultHandler;
import gov.usgs.owi.nldi.services.Parameters;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class NavigationDao extends BaseDao {

  // deprecated
  public static final String NAVIGATE = "navigate";

  public static final String NAVIGATION = "navigation";

  public static final String NAVIGATE_CACHED = NAVIGATE + "_cached";
  private static final String NS = NAVIGATE + ".";
  private static final String GET_CACHE = "getCache";

  @Autowired
  public NavigationDao(SqlSessionFactory sqlSessionFactory) {
    super(sqlSessionFactory);
  }

  public Map<String, String> navigate(Map<String, Object> parameterMap) {
    return getSqlSession().selectOne(NS + NAVIGATE, parameterMap);
  }

  public String getCache(Map<String, Object> parameterMap) {
    return getSqlSession().selectOne(NS + GET_CACHE, parameterMap);
  }

  public FeatureList navigateFeatures(
      @NonNull Integer comid,
      @NonNull String dataSource,
      @NonNull String navigationMode,
      @NonNull BigDecimal distance) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put(Parameters.COMID, comid);
    parameters.put(BaseController.DATA_SOURCE, dataSource);
    parameters.put(Parameters.NAVIGATION_MODE, navigationMode);
    parameters.put(Parameters.DISTANCE, distance);

    FeatureListResultHandler resultHandler = new FeatureListResultHandler();

    getSqlSession().select(NS + "features", parameters, resultHandler);

    return resultHandler.getFeatureList();
  }
}
