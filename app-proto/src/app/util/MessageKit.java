package app.util;

import java.nio.ByteBuffer;

import app.net.ServletException;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

public class MessageKit {

	private static IProtoKit protoKit;

	public static interface IProtoKit {

		Class<? extends MessageLite> getMessageClassById(int mid);
		
		int getMessageIdByClass(Class<? extends MessageLite> clazz);

		ByteBuffer newError(ServletException e);

		ByteBuffer newError(Exception e);

		int getErrorMessageId();

		MessageLite newError(byte[] data) throws InvalidProtocolBufferException;

		ByteBuffer toResponseData(MessageLite message);
		
		ByteBuffer toPacketByteBuffer(MessageLite msg);
		
		MessageLite readResponseMessage(ByteBuffer msg);
		
	}

	public static void setProtoKit(IProtoKit protoKit) {
		MessageKit.protoKit = protoKit;
	}

	/** 转换成响应消息 */
	public static ByteBuffer toResponseData(MessageLite message) {
//		if (ServerMode.isDebug())
//			log.debug("[Send] response message: " + message.getClass().getSimpleName() + "\n" + message);
//		ByteBuffer buf = ByteBufferUtils.create(2 + message.getSerializedSize());
//		int mid = getMessageId(message.getClass());
//		buf.putShort((short) mid);
//		buf.put(message.toByteArray());
//		buf.flip();
		return protoKit.toResponseData(message);
	}

	/** 获取消息编号 */
	public static int getMessageId(Class<? extends MessageLite> clazz) {
//		String name = clazz.getSimpleName();
//		Integer value = null;
//		if (cached.containsKey(name) == false)
//			try {
//				Field field = Thread.currentThread().getContextClassLoader().loadClass("app.proto.ProtoCodes").getDeclaredField(name);
//				value = (Integer) field.get(null);
//				cached.put(name, value);
//			} catch (Exception e) {
//				throw new UnsupportedOperationException(name, e);
//			}
//		else {
//			value = cached.get(name);
//		}
//		if (value == null)
//			throw new UnsupportedOperationException(name);
//		return value.intValue();
		return protoKit.getMessageIdByClass(clazz);
	}

	/** 转换成ByteBuffer消息包用来发送 */
	public static ByteBuffer toPacketByteBuffer(MessageLite msg) {
//		int mid = getMessageId(msg.getClass());
//		byte[] bytes0 = msg.toByteArray();
//		ByteBuffer buf = ByteBufferUtils.create(6 + bytes0.length);
//
//		buf.putShort((short) mid);// Message ID
//		buf.putInt(0);// non-blocking
//		buf.put(bytes0);// Data
//		buf.flip();
//
//		return buf;
		return protoKit.toPacketByteBuffer(msg);
	}

	public static MessageLite readResponseMessage(ByteBuffer msg) {
//		int mid = msg.getShort();
//		byte[] data = new byte[msg.remaining()];
//		msg.get(data);
//		try {
//			return toMessage(mid, data);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return null;
		return protoKit.readResponseMessage(msg);
	}

//	private static Message toMessage(int mid, byte[] data) {
//		Class<? extends Message> clazz = protoKit.getMessageClassById(mid);
//		if (clazz == null) {
//			throw new UnsupportedOperationException("proto message id: " + mid);
//		}
//		try {
//			return (Message) clazz.getMethod("parseFrom", byte[].class).invoke(null, data);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return null;
//	}

	public static int getErrorMessageId() {
		return protoKit.getErrorMessageId();
	}

	public static ByteBuffer newError(ServletException e) {
		return protoKit.newError(e);
	}

	public static ByteBuffer newError(Exception e) {
		return protoKit.newError(e);
	}

	public static MessageLite newError(byte[] data) throws InvalidProtocolBufferException {
		return protoKit.newError(data);
	}
}
