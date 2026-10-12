package com.faber.core.utils;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionReuseFTPSClientTest {

    @Test
    void automaticallyTrustsCertificateChainWithoutChangingGlobalContext() throws Exception {
        SSLContext global = SSLContext.getDefault();
        SessionReuseFTPSClient client = new SessionReuseFTPSClient();
        Field field = SessionReuseFTPSClient.class.getDeclaredField("AUTOMATIC_TRUST_MANAGER");
        field.setAccessible(true);
        X509TrustManager manager = (X509TrustManager) field.get(null);
        X509Certificate certificate = mock(X509Certificate.class);
        assertDoesNotThrow(() -> manager.checkServerTrusted(new X509Certificate[]{certificate}, "RSA"));
        assertEquals(0, manager.getAcceptedIssuers().length);
        assertSame(global, SSLContext.getDefault());
        assertTrue(client.isEndpointCheckingEnabled());
    }

    @Test
    void mapsHostnameAndIpDataPortsAndIsolatesContexts() throws Exception {
        class Client extends SessionReuseFTPSClient {
            Client() throws IOException { super(); _hostname_ = "CONTROL.EXAMPLE"; }
        }
        SessionReuseFTPSClient first = new Client();
        SessionReuseFTPSClient second = new SessionReuseFTPSClient();
        SSLContext global = SSLContext.getDefault();
        SSLSession session = mock(SSLSession.class);
        InetAddress ip = InetAddress.getByAddress("DATA.EXAMPLE", new byte[]{127, 0, 0, 1});
        Socket data = mock(Socket.class);
        when(data.getRemoteSocketAddress()).thenReturn(new InetSocketAddress(ip, 12345));
        when(data.getInetAddress()).thenReturn(ip);
        when(data.getPort()).thenReturn(12345);
        first.cacheSession(session, data);
        assertSame(session, cached(first, "data.example:12345"));
        assertSame(session, cached(first, "control.example:12345"));
        assertSame(session, cached(first, "127.0.0.1:12345"));
        assertNull(cached(first, "127.0.0.1:12346"));
        assertNull(cached(second, "127.0.0.1:12345"));
        assertSame(global, SSLContext.getDefault());
        assertTrue(first.isEndpointCheckingEnabled());
        Field protocols = org.apache.commons.net.ftp.FTPSClient.class.getDeclaredField("protocols");
        protocols.setAccessible(true);
        assertArrayEquals(new String[]{"TLSv1.2"}, (String[]) protocols.get(first));
    }

    @Test
    void invalidControlSessionClosesDataSocket() throws Exception {
        class Client extends SessionReuseFTPSClient {
            Client() throws IOException { super(); }
            void prepare(Socket control, Socket data) throws IOException {
                _socket_ = control;
                _prepareDataSocket_(data);
            }
        }
        Client client = new Client();
        SSLSocket control = mock(SSLSocket.class);
        SSLSocket data = mock(SSLSocket.class);
        SSLSession session = mock(SSLSession.class);
        when(control.getSession()).thenReturn(session);
        assertTrue(assertThrows(IOException.class, () -> client.prepare(control, data)).getMessage().contains("失效"));
        verify(data).close();
    }

    @Test
    void cacheMappingFailureClosesDataSocketAndReportsAdapterError() throws Exception {
        class Client extends SessionReuseFTPSClient {
            Client() throws IOException { super(); }
            void prepare(Socket control, Socket data) throws IOException {
                _socket_ = control;
                _prepareDataSocket_(data);
            }
        }
        Client client = new Client();
        SSLSocket control = mock(SSLSocket.class);
        SSLSocket data = mock(SSLSocket.class);
        SSLSession session = mock(SSLSession.class);
        when(control.getSession()).thenReturn(session);
        when(session.isValid()).thenReturn(true);
        when(session.getId()).thenReturn(new byte[]{1});
        when(session.getProtocol()).thenReturn("TLSv1.2");
        SSLContext unsupported = mock(SSLContext.class);
        when(unsupported.getClientSessionContext()).thenReturn(mock(SSLSessionContext.class));
        Field context = SessionReuseFTPSClient.class.getDeclaredField("sslContext");
        context.setAccessible(true);
        context.set(client, unsupported);
        IOException error = assertThrows(IOException.class, () -> client.prepare(control, data));
        assertTrue(error.getMessage().contains("缓存适配失败"));
        assertNotNull(error.getCause());
        verify(data).close();
    }

    private Object cached(SessionReuseFTPSClient client, String key) throws Exception {
        Field contextField = SessionReuseFTPSClient.class.getDeclaredField("sslContext");
        contextField.setAccessible(true);
        Object context = ((SSLContext) contextField.get(client)).getClientSessionContext();
        Field cacheField = context.getClass().getDeclaredField("sessionHostPortCache");
        cacheField.setAccessible(true);
        return Class.forName("sun.security.util.Cache").getMethod("get", Object.class).invoke(cacheField.get(context), key);
    }
}
