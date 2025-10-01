package gov.usgs.owi.nldi.dao;

import java.util.Map;

import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StreamingDao extends BaseDao {

	public static final String SESSION_ID = "sessionId";

	private static final String NS = "stream.";

	@Autowired
	public StreamingDao(SqlSessionFactory sqlSessionFactory) {
		super(sqlSessionFactory);
	}

	public void stream(String featureType, Map<String, Object> parameterMap, ResultHandler<?> handler) {
		if (null == handler) {
			throw new IllegalArgumentException("A ResultHandler is required for the StreamingDao.stream");
		}
		getSqlSession().select(NS + featureType, parameterMap, handler);
	}

}
