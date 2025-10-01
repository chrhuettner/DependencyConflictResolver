package gov.usgs.owi.nldi.dao;

import java.math.BigInteger;
import java.util.Map;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LogDao extends BaseDao {

	public static final String REFERER = "referer";
	public static final String USER_AGENT = "userAgent";
	public static final String REQUEST_URI = "requestUri";
	public static final String QUERY_STRING = "queryString";
	public static final String ID = "id";
	public static final String HTTP_STATUS_CODE = "httpStatusCode";

	private static final String NS = "log.";
	private static final String START = "start";
	private static final String END = "end";

	@Autowired
	public LogDao(SqlSessionFactory sqlSessionFactory) {
		super(sqlSessionFactory);
	}

	public BigInteger start(Map<String, Object> parameterMap) {
		return getSqlSession().selectOne(NS + START, parameterMap);
	}

	public void end(Map<String, Object> parameterMap) {
		getSqlSession().selectOne(NS + END, parameterMap);
	}

}
