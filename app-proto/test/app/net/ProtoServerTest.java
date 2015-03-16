package app.net;

import java.io.IOException;

import junit.framework.TestCase;

public class ProtoServerTest extends TestCase {

	public void testStartup2Shutdown() {
		ProtoServer server = new ProtoServer();

		try {
			server.start(9000);
		} catch (IOException e) {
			e.printStackTrace();
		}
		assertTrue(server.isRuning());

		server.stop();
		assertTrue(!server.isRuning());
	}

}
