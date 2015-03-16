package app.net;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntObjectProcedure;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import app.util.IMethodHandler;
import app.util.IProtoProxyKit;
import app.util.MessageKit;
import app.util.ProtoProxyKit;
import app.util.ServerMode;

import com.google.protobuf.MessageLite;

import app.core.Connection;
import app.core.MessageOutput;
import app.event.AppEventListener;
import app.event.AppEventManager;
import app.filter.FilterAdapter;
import app.filter.IFilterChain;
import app.filter.IFilterChain.FilterChain;
import app.filter.IProtocolEncodeFilter;
import app.net.AppHandler;
import app.net.AppRequest;
import app.net.AppRequestDispatcher;
import app.net.AppResponse;
import app.net.AppServlet;
import app.net.Code;

public class ProtoDispatcher extends FilterAdapter implements
		AppRequestDispatcher, IProtocolEncodeFilter, IMethodHandler {
	protected Logger log = Logger.getLogger(getClass());
	protected IProtoProxyKit protoProxyKit = new ProtoProxyKit();
	protected AppHandler handler;
	protected TIntObjectMap<List<AppServlet>> servlets = new TIntObjectHashMap<List<AppServlet>>();
	protected TIntObjectMap<List<AppEventListener>> listeners = new TIntObjectHashMap<List<AppEventListener>>();

	@Override
	public void init(AppHandler handler) {
		if (this.handler != null)
			return;
		this.handler = handler;
		
		List<AppServlet> inited = new ArrayList<AppServlet>();
		for (List<AppServlet> list : servlets.valueCollection())
			for (AppServlet servlet : list) {
				try {
					if (inited.contains(servlet))
						continue;
					inited.add(servlet);
					servlet.init(handler);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		registListeners();
		
		// ProtocolBuffer: Message Encode Filter
		handler.getFilterChain().addFirstFilter(
				IFilterChain.FILTER_PROTOCOL_ENCODE, this);
	}

	protected void registListeners() {
		listeners.forEachEntry(new TIntObjectProcedure<List<AppEventListener>>() {
			@Override
			public boolean execute(int eventType, List<AppEventListener> list) {
				for (AppEventListener listener : list) {
					AppEventManager.getInstance().addListener(eventType, listener);
				}
				return true;
			}
		});
	}

	@Override
	public void dispatch(AppRequest request, AppResponse response)
			throws Exception {
		int mode = readMode(request);// read mode

		// Miss servlet
		if (hasServlet(mode) == false) {
			log.warn(String.format("Miss servlet: %d  -  %s", mode, request
					.getSession().getConnection()));
			return;
		}
		// Dispatch message to Servlet all
		try {
			ByteBuffer buf = request.getByteBuffer();
			List<AppServlet> list = servlets.get(mode);
			for (int i = 0, size = list.size(); i < size; i++) {
				// backup
				int pos = buf.position();
				int limit = buf.limit();

				AppServlet servlet = list.get(i);
				servlet.service(request, response);// handle

				// restore
				buf.position(pos);
				buf.limit(limit);
			}
		} catch (ServletException e) {
			e.printStackTrace();
			response.setData(MessageKit.newError(e));
			response.setStatus((short) 503);
		} catch (Exception e) {
			e.printStackTrace();
			response.setData(MessageKit.newError(e));
			response.setStatus((short) 500);
		}
	}

	protected int readMode(AppRequest request) {
		return request.getMode();
	}

	@Override
	public boolean hasServlet(int mode) {
		return servlets.containsKey(mode) && servlets.get(mode).size() > 0;
	}

	@Override
	public void addServlet(int mode, AppServlet servlet) {
		List<AppServlet> list = servlets.get(mode);
		if (list == null) {
			list = new ArrayList<AppServlet>();
			servlets.put(mode, list);
		}
		if (list.contains(servlet) == false) {
			list.add(servlet);
			log.debug("addServlet(" + mode + "): " + servlet.getClass());
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public AppServlet addServlet(String id, String servletClass) throws ClassNotFoundException {
		Class<? extends AppServlet> clazz = (Class<? extends AppServlet>) Thread.currentThread().getContextClassLoader().loadClass(servletClass);
		Code code = clazz.getAnnotation(Code.class);
		AppServlet servlet = null;
		
		// Method-Proxy
		
		try {
			servlet = (AppServlet) protoProxyKit.proxyServletClass(clazz, this);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} 
		
		if (handler != null) {
			servlet.init(handler);
		}
		if (id != null) {
			// from config
			int mode = Integer.parseInt(id);
			addServlet(mode, servlet);
		} else if (code != null) {
			// Class-Annotation: @Code(xxx)
			int mode = code.value();
			addServlet(mode, servlet);
		} else {
		}
		return servlet;
	}

	@Override
	public void handleEventMethod(int eventType, Object instance) {
		List<AppEventListener> list = null;
		if(listeners.containsKey(eventType)){
			list = listeners.get(eventType);
		}else{
			list = new ArrayList<AppEventListener>();
			listeners.put(eventType, list);
		}
		AppEventListener listener = (AppEventListener) instance;
		if(list.contains(listener)==false){
			list.add(listener);
		}
	}

	@Override
	public void handleServiceMethod(int code, Object instance) {
		AppServlet servlet = (AppServlet) instance;
		addServlet(code, servlet);
	}

	// private String getMessageNameByCode(int code) {
	// Class<?>[] classes = Messages.class.getClasses();
	// // int index = (classes.length - 1) - ((code - 1) % 10000) * 2 - 1;
	// int index = ((code - 1) % 10000) * 2;
	// String name = classes[index].getCanonicalName();
	// return name;
	// }

	@Override
	public AppServlet removeServlet(int mode) {
		AppServlet servlet = null;
		List<AppServlet> list = servlets.get(mode);
		if (list != null && list.size() > 0) {
			servlet = list.remove(list.size() - 1);
		}
		return servlet;
	}

	@Override
	public void destroy() {
		if (handler == null)
			return;

		handler.getFilterChain().removeFilter(
				IFilterChain.FILTER_PROTOCOL_ENCODE, this);
		
		unregistListeners();
		
		List<AppServlet> inited = new ArrayList<AppServlet>();
		for (List<AppServlet> list : servlets.valueCollection())
			for (AppServlet servlet : list) {
				try {
					if (inited.contains(servlet))
						continue;
					inited.add(servlet);
					servlet.destroy();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		handler = null;
	}

	protected void unregistListeners() {
		listeners.forEachEntry(new TIntObjectProcedure<List<AppEventListener>>() {
			@Override
			public boolean execute(int eventType, List<AppEventListener> list) {
				for (AppEventListener listener : list) {
					AppEventManager.getInstance().removeListener(eventType, listener);
				}
				return true;
			}
		});
	}

	@Override
	public void messageEncode(Connection conn, Object message,
			MessageOutput out, FilterChain<IProtocolEncodeFilter> chain)
			throws Exception {

		// ProtocolBuffer: NBMessage Filter
		if (message instanceof MessageLite) {
			if (!message.getClass().getSimpleName().startsWith("SC")) {
				AppServer server = (AppServer) conn.getSession()
						.getServerHandler().getConnector();
				if (server.getPort() > 0) {
					throw new UnsupportedOperationException(
							"message isn't SCxxx : " + message.getClass());
				}
			}
			MessageLite msg = (MessageLite) message;
			message = MessageKit.toPacketByteBuffer(msg);
			if (ServerMode.isDebug())
				log.debug("[Send] non-blocking message: "
						+ msg.getClass().getSimpleName() + "\n" + msg);
		}

		if (chain.hasNext()) {
			chain.nextFilter().messageEncode(conn, message, out,
					chain.getNext());
		} else {
			out.putLast(message);
		}
	}

	@Override
	public String toString() {
		return super.toString() + "  servlets:" + servlets;
	}
}
