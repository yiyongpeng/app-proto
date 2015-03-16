package app.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import app.event.AppEvent;
import app.event.AppEventListener;
import app.event.OPEvent;
import app.net.AppServlet;
import app.net.AppSession;
import app.net.Service;
import app.net.ServletException;
import app.util.IMethodHandler;

import com.google.protobuf.MessageLite;

public class ProtoProxyKit implements IProtoProxyKit{
	private static final Logger log = Logger.getLogger(ProtoProxyKit.class);

	public static String PLAYER_CLASSNAME = "app.game.player.IPlayer";

	public ProtoProxyKit() {
		// TODO Auto-generated constructor stub
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public AppServlet proxyServletClass(Class<? extends AppServlet> clazz,
			IMethodHandler methodHandler) throws InstantiationException, IllegalAccessException {
		// Code => Methods
		Map<Integer, List<Method>> codeMethods = new HashMap<Integer, List<Method>>();
		Map<Integer, List<Method>> eventMethods = new HashMap<Integer, List<Method>>();
		for (Method method : clazz.getDeclaredMethods())
			try {
				// OPEvent Method
				OPEvent opEvent = method.getAnnotation(OPEvent.class);
				if (opEvent != null) {
					Class[] params = method.getParameterTypes();
					if (params.length > 1
							|| (params.length == 1 && !AppEvent.class
									.isAssignableFrom(params[0]))) {
						throw new IllegalStateException(method
								+ "  Parameters Error!");
					}
					int code = opEvent.value();
					List<Method> methodList = eventMethods.get(code);
					if (methodList == null) {
						methodList = new ArrayList<Method>();
						eventMethods.put(code, methodList);
					}
					methodList.add(method);
				}

				// Server Method
				Service serviceFlag = method.getAnnotation(Service.class);
				if (serviceFlag != null) {
					// Validate parameters
					Class[] params = method.getParameterTypes();
					if (params.length != 2)
						throw new IllegalStateException(method
								+ "  Parameters Error!");
					if ((params[0] != AppSession.class && !params[0].getName().equals(PLAYER_CLASSNAME))
							|| !MessageLite.class.isAssignableFrom(params[1]))
						throw new IllegalStateException(method
								+ "  Parameters Error!");
					if (method.getReturnType() != void.class
							&& !MessageLite.class.isAssignableFrom(method
									.getReturnType()))
						throw new IllegalStateException(method
								+ "  Parameter-Return Error!");
					Class<?>[] exceps = method.getExceptionTypes();
					if (exceps.length >= 1) {
						for (Class<?> excep : exceps) {
							if (excep != ServletException.class) {
								throw new IllegalStateException(method
										+ "  Parameter-Throws Error!");
							}
						}
					}
					// Valid method
					int code = MessageKit.getMessageId(params[1]);
					List<Method> methodList = codeMethods.get(code);
					if (methodList == null) {
						methodList = new ArrayList<Method>();
						codeMethods.put(code, methodList);
					}
					methodList.add(method);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		if (codeMethods.isEmpty() && eventMethods.isEmpty())
			return clazz.newInstance();

		ClassPool pool = getClassPool();
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
//		ClassPath cp = new LoaderClassPath(clazz.getClassLoader());
//		pool.insertClassPath(cp);
		
		// GenProxy
//		List<ClassClassPath> cplist = new ArrayList<ClassClassPath>();
		CtClass oldClass = null;
//		ClassClassPath classcp = new ClassClassPath(clazz);
//		pool.insertClassPath(classcp);
//		cplist.add(classcp);
		try {
			oldClass = pool.get(clazz.getName());
		} catch (NotFoundException e) {
			throw new RuntimeException(e);
		}
//		pool.removeClassPath(classcp);
		String proxyClassName = clazz.getName() + "$Proxy"+loader.hashCode()+"c"+clazz.hashCode();
		proxyClassName = proxyClassName.replace("-", "_");
		CtClass newClass = null;
		try {
			clazz = (Class<? extends AppServlet>) loader.loadClass(proxyClassName);
		} catch (ClassNotFoundException e) {
			try{
				newClass = pool.get(proxyClassName);
			}catch (NotFoundException e1) {
				newClass = pool.makeClass(proxyClassName, oldClass);
				// Servlet Dispatch
				if (codeMethods.isEmpty() == false) {
					StringBuilder sb = new StringBuilder(
							"public void service(app.net.AppRequest request, app.net.AppResponse response) throws app.net.ServletException {\n");
					sb.append("  app.net.AppSession session = request.getSession();\n");
					sb.append("  int pos = request.getByteBuffer().position();\n");
					sb.append("  super.service($$);\n");
					sb.append("  request.getByteBuffer().position(pos);\n");
					sb.append("  byte [] bytes0 = null;\n");
					sb.append("  int code = request.getMode();\n");
					sb.append("  switch (code) {\n");
					
	//				cplist.add(cp);
					for (Entry<Integer, List<Method>> entry : codeMethods
							.entrySet()) {
						int code = entry.getKey();
						List<Method> methods = entry.getValue();
						Class<?> messageClass = methods.get(0).getParameterTypes()[1];
						String messageName = messageClass.getCanonicalName();// getMessageNameByCode(code);
	//					cp = new ClassClassPath(messageClass);
	//					pool.insertClassPath(cp);
	//					cplist.add(cp);
						sb.append(String.format("    case %d :\n", code));
						sb.append("      bytes0 = new byte[request.getByteBuffer().remaining()];\n");
						sb.append("      request.getByteBuffer().get(bytes0);\n");
						sb.append(String.format(
								"      %s m%d = %s.parseFrom(bytes0);\n",
								messageName, code, messageName));
						sb.append("      if(app.util.ServerMode.isDebug())log.debug(\"[Recv] "
								+ messageName + ":\\n\" + m" + code + ");\n");
						for (Method method : methods) {
							log.debug(String.format("[%d]%s", code, method));
	
							String session = "null";
							Class<?> param1Class = method.getParameterTypes()[0];
	
							if (param1Class.getName().equals(PLAYER_CLASSNAME))
								session = "(" + PLAYER_CLASSNAME
										+ ")session.getAttribute(\"__PLAYER__\")";
							else if (param1Class == AppSession.class)
								session = "session";
	
							if (method.getReturnType() == void.class) {
								sb.append(String.format("      %s(%s, m%d);\n",
										method.getName(), session, code));
							} else {
								sb.append(String
										.format("      com.google.protobuf.MessageLite data = %s(%s, m%d);\n",
												method.getName(), session, code));
								sb.append("      if(data!=null) response.setData(app.util.MessageKit.toResponseData(data));\n");
							}
						}
						sb.append("      break;\n");
					}
					sb.append("  }\n");
					sb.append("}\n");
					
					if(log.isDebugEnabled())
					log.debug(new StringBuilder("[Proxy] ").append(proxyClassName).
							append("(pool-loader:").append(pool.getClass().getClassLoader()).append(",class-loader:").append(loader).append(")\n").append(sb));
	
					try {
						newClass.addMethod(CtMethod.make(sb.toString(), newClass));
					} catch (CannotCompileException e2) {
						throw new RuntimeException(e2);
					}
					sb.setLength(0);
				}
				// AppEventListener Interface
				if (eventMethods.isEmpty() == false) {
					CtClass anInterface;
					try {
						anInterface = pool.get(AppEventListener.class.getName());
					} catch (NotFoundException e2) {
						throw new RuntimeException(e2);
					}
					newClass.addInterface(anInterface);
					StringBuilder sb = new StringBuilder(
							"public void onAppEvent(app.event.AppEvent event) {\n");
					sb.append("  int code = event.getType();\n");
					sb.append("  switch (code) {\n");
					for (Entry<Integer, List<Method>> entry : eventMethods
							.entrySet()) {
						int code = entry.getKey();
						List<Method> methods = entry.getValue();
						sb.append(String.format("    case %d :\n", code));
						for (Method method : methods) {
							log.debug(String.format("[%d]%s", code, method));
	
							Class<?>[] paramsClass = method.getParameterTypes();
							String invokeCode = "";
	
							if (paramsClass.length == 1)
								invokeCode = String.format("      %s(%s event);\n",
										method.getName(),
										paramsClass[0] == AppEvent.class ? "" : "("
												+ paramsClass[0].getName() + ")");
							else
								invokeCode = String.format("      %s();\n",
										method.getName());
	
							sb.append(invokeCode);
						}
						sb.append("      break;\n");
					}
					sb.append("  }\n");
					sb.append("}\n");
					
					if(log.isDebugEnabled())
					log.debug(new StringBuilder("[Proxy] ").append(proxyClassName).append("(pool-loader:").append(pool.getClass().getClassLoader()).append(",class-loader:").append(loader).append(")\n").append(sb));
	
					try {
						newClass.addMethod(CtMethod.make(sb.toString(), newClass));
					} catch (CannotCompileException e2) {
						throw new RuntimeException(e2);
					}
					sb.setLength(0);
				}
			}
			try {
				clazz = newClass.toClass(loader, null);
			} catch (CannotCompileException e1) {
				throw new RuntimeException(e1);
			}
		}
		
//		pool.removeClassPath(cp);
//		for (ClassClassPath cp : cplist) {
//			pool.removeClassPath(cp);
//		}
		
		AppServlet instance = clazz.newInstance();
		
		// OPEvent Instance
		for (Integer eventType : eventMethods.keySet()) {
			methodHandler.handleEventMethod(eventType, instance);
		}
		// Servlet Instance
		for (Integer code : codeMethods.keySet()) {
			methodHandler.handleServiceMethod(code, instance);
		}
		return instance;
	}

	protected ClassPool getClassPool() {
		Object appPool = ThreadContext.contains()&&ThreadContext.contains("ClassPool")?ThreadContext.getAttribute("ClassPool"):null;
		ClassPool pool = (appPool !=null && (appPool instanceof ClassPool))?(ClassPool)appPool:ClassPool.getDefault();
		return pool;
	}
}
