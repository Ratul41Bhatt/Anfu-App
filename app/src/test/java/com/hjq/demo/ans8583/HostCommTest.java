package com.hjq.demo.ans8583;

import org.junit.Test;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class HostCommTest {
    @Test public void framesRequestAndReadsFragmentedResponse() throws Exception {
        try (ServerSocket host = new ServerSocket(0)) {
            FutureTask<byte[]> server = new FutureTask<>(() -> {
                try (Socket peer = host.accept()) {
                    DataInputStream input = new DataInputStream(peer.getInputStream());
                    byte[] request = new byte[input.readUnsignedShort()];
                    input.readFully(request);
                    for (byte b : new byte[]{0, 3, 10, 20, 30}) {
                        peer.getOutputStream().write(b);
                        peer.getOutputStream().flush();
                    }
                    return request;
                }
            });
            new Thread(server).start();
            boolean[] sent = {false};
            assertArrayEquals(new byte[]{10, 20, 30}, HostComm.exchange("127.0.0.1",
                    host.getLocalPort(), 2000, new byte[]{1, 2}, sent));
            assertTrue(sent[0]);
            assertArrayEquals(new byte[]{1, 2}, server.get(3, TimeUnit.SECONDS));
        }
    }

    @Test public void truncatedReplyLeavesTransactionUncertain() throws Exception {
        assertUncertain(new byte[]{0, 4, 1});
    }

    @Test public void invalidFrameLeavesTransactionUncertain() throws Exception {
        assertUncertain(new byte[]{0, 0});
    }

    private void assertUncertain(byte[] reply) throws Exception {
        try (ServerSocket host = new ServerSocket(0)) {
            FutureTask<Void> server = new FutureTask<>(() -> {
                try (Socket peer = host.accept()) {
                    DataInputStream input = new DataInputStream(peer.getInputStream());
                    byte[] request = new byte[input.readUnsignedShort()];
                    input.readFully(request);
                    peer.getOutputStream().write(reply);
                }
                return null;
            });
            new Thread(server).start();
            boolean[] sent = {false};
            try {
                HostComm.exchange("127.0.0.1", host.getLocalPort(), 2000, new byte[]{1}, sent);
                fail("Invalid response accepted");
            } catch (IOException expected) {
                assertTrue("Request may have reached the host", sent[0]);
            }
            server.get(3, TimeUnit.SECONDS);
        }
    }

    @Test public void connectionRefusedDoesNotArmAReversal() throws Exception {
        int unusedPort;
        try (ServerSocket reservation = new ServerSocket(0)) {
            unusedPort = reservation.getLocalPort();
        }
        boolean[] sent = {false};
        try {
            HostComm.exchange("127.0.0.1", unusedPort, 1000, new byte[]{1}, sent);
            fail("Expected connection refusal");
        } catch (IOException expected) {
            assertFalse(sent[0]);
        }
    }
}
