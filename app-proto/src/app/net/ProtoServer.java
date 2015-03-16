package app.net;

import java.nio.ByteBuffer;

import com.google.protobuf.MessageLite;

import app.net.AppServer;
import app.util.ByteBufferUtils;
import app.util.MessageKit;
import app.util.ServerMode;

public class ProtoServer extends AppServer implements IAppMessageConverter {

	public ProtoServer() {
		super();

		getHandler().setDispatcher(new ProtoDispatcher());
	}

	@Override
	protected void init() {
		super.init();
		getHandler().setAttribute(AppSession.SESSION_MESSAGE_CONVERTER, this);
	}

	@Override
	public ByteBuffer toByteBuffer(Object message) {
		if (message instanceof MessageLite) {
			MessageLite msg = (MessageLite) message;
			if (ServerMode.isDebug())
				log.debug("[Send] blocking message: "
						+ message.getClass().getSimpleName() + "\n" + message);
			return ByteBufferUtils.create(msg.toByteArray());
		}
		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public int toMessageMode(Object message) {
		if (message instanceof MessageLite)
			return MessageKit.getMessageId((Class<? extends MessageLite>) message
					.getClass());
		return 0;
	}

}
