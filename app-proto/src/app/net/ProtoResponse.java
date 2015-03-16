package app.net;

import java.nio.ByteBuffer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import app.util.MessageKit;

public abstract class ProtoResponse<M extends MessageLite,E extends MessageLite> extends
		AppCallResponseAdapter {

	@Override
	@SuppressWarnings("unchecked")
	public void onSuccess(ByteBuffer msg) {
		MessageLite message = MessageKit.readResponseMessage(msg);
		onProtoSuccess((M) message);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onFailed(int status, ByteBuffer msg) {
		int mid = msg.getShort();

		if (mid == MessageKit.getErrorMessageId()) {
			byte[] data = new byte[msg.remaining()];
			msg.get(data);

			try {
				E error = (E) MessageKit.newError(data);
				onProtoFault(error);
			} catch (InvalidProtocolBufferException e) {
				e.printStackTrace();
			}
		} else {
			log.error("Unkown response fault: mode=" + mid);
		}
	}

	protected abstract void onProtoSuccess(M message);

	protected void onProtoFault(E error) {
		log.error("Recv response fault:\n" + error);
	}

}
