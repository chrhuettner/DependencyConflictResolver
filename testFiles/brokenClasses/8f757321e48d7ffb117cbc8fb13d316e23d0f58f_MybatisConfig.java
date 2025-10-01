package gov.usgs.owi.nldi.springinit;

import gov.usgs.owi.nldi.typehandler.GeometryTypeHandler;
import java.util.LinkedHashMap;
import javax.sql.DataSource;
import mil.nga.sf.geojson.Geometry;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Configuration
public class MybatisConfig {

  public static final String MYBATIS_MAPPERS = "mybatis/*.xml";
  public static final String LINKED_HASH_MAP_ALIAS = "LinkedHashMap";

  @Autowired DataSource dataSource;

  @Bean
  public org.apache.ibatis.session.Configuration mybatisConfiguration() {
    org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
    config.setCallSettersOnNulls(true);
    config.setCacheEnabled(false);
    config.setLazyLoadingEnabled(false);
    config.setAggressiveLazyLoading(false);
    config
        .getVariables()
        .put("coastal_fcode", "56600"); // global constant for coastal feature fcode values

    registerAliases(config.getTypeAliasRegistry());
    registerHandlers(config.getTypeHandlerRegistry());

    return config;
  }

  @Bean
  public SqlSessionFactoryBean sqlSessionFactory() throws Exception {
    SqlSessionFactoryBean sqlSessionFactory = new SqlSessionFactoryBean();
    sqlSessionFactory.setConfiguration(mybatisConfiguration());
    sqlSessionFactory.setDataSource(dataSource);
    Resource[] mappers = new PathMatchingResourcePatternResolver().getResources(MYBATIS_MAPPERS);
    sqlSessionFactory.setMapperLocations(mappers);
    return sqlSessionFactory;
  }

  private void registerAliases(TypeAliasRegistry registry) {
    registry.registerAlias(LINKED_HASH_MAP_ALIAS, LinkedHashMap.class);
  }

  private void registerHandlers(TypeHandlerRegistry registry) {
    registry.register(Geometry.class, GeometryTypeHandler.class);
  }
}
