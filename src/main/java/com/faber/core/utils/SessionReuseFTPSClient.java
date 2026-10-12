package com.faber.core.utils;

import org.apache.commons.net.ftp.FTPSClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Locale;

/** 显式 FTPS，复用控制连接的 TLS 1.2 会话。 */
public class SessionReuseFTPSClient extends FTPSClient {

    // 按当前需求自动信任证书链，仅用于本客户端的 SSLContext，仍校验服务器主机名。
    private static final X509TrustManager AUTOMATIC_TRUST_MANAGER = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    private final SSLContext sslContext;
    private Socket pendingDataSocket;

    public SessionReuseFTPSClient() throws IOException {
        this(createContext());
    }

    private SessionReuseFTPSClient(SSLContext context) {
        super(false, context);
        this.sslContext = context;
        setEnabledProtocols(new String[]{"TLSv1.2"});
        setEndpointCheckingEnabled(true);
    }

    private static SSLContext createContext() throws IOException {
        try {
            SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(null, new TrustManager[]{AUTOMATIC_TRUST_MANAGER}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IOException("FTPS TLS 上下文初始化失败", e);
        }
    }

    @Override
    protected Socket _openDataConnection_(String command, String argument) throws IOException {
        try {
            return super._openDataConnection_(command, argument);
        } catch (IOException | RuntimeException e) {
            if (pendingDataSocket != null) {
                try {
                    pendingDataSocket.close();
                } catch (IOException closeError) {
                    e.addSuppressed(closeError);
                }
            }
            throw e;
        } finally {
            pendingDataSocket = null;
        }
    }

    @Override
    protected void _prepareDataSocket_(Socket socket) throws IOException {
        if (socket == null) return; // Commons Net 在服务器拒绝数据连接时传入 null。
        pendingDataSocket = socket;
        try {
            if (!(socket instanceof SSLSocket) || !(_socket_ instanceof SSLSocket)) {
                throw new IOException("FTPS 控制连接和数据连接必须使用 TLS（PROT P）");
            }
            SSLSession session = ((SSLSocket) _socket_).getSession();
            if (!session.isValid() || session.getId().length == 0 || !"TLSv1.2".equals(session.getProtocol())) {
                throw new IOException("FTPS 控制连接 TLS 1.2 会话已失效，无法复用");
            }
            cacheSession(session, socket);
            SSLParameters parameters = ((SSLSocket) socket).getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            ((SSLSocket) socket).setSSLParameters(parameters);
        } catch (IOException | RuntimeException e) {
            try {
                socket.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    void cacheSession(SSLSession session, Socket socket) throws IOException {
        // shortcut: 依赖 OpenJDK 的缓存结构，升级 JDK 时须重新验证目录列表及连续下载。
        try {
            Object sessionContext = sslContext.getClientSessionContext();
            Field field = sessionContext.getClass().getDeclaredField("sessionHostPortCache");
            field.setAccessible(true);
            Object cache = field.get(sessionContext);
            Method put = Class.forName("sun.security.util.Cache").getMethod("put", Object.class, Object.class);
            InetSocketAddress address = (InetSocketAddress) socket.getRemoteSocketAddress();
            for (String host : new String[]{_hostname_, address.getHostString(), socket.getInetAddress().getHostAddress(),
                    socket.getInetAddress().getHostName()}) {
                if (host != null) {
                    put.invoke(cache, (host + ":" + socket.getPort()).toLowerCase(Locale.ENGLISH), session);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IOException("FTPS TLS 会话缓存适配失败：请核对 OpenJDK 版本及 sun.security.ssl、sun.security.util 的 --add-opens 参数", e);
        }
    }
}
