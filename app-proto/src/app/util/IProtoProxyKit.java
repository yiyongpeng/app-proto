package app.util;

import app.net.AppServlet;

public interface IProtoProxyKit {

	AppServlet proxyServletClass(Class<? extends AppServlet> clazz,
			IMethodHandler methodHandler) throws InstantiationException, IllegalAccessException;

}
