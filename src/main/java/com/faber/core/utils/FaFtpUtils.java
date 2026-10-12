package com.faber.core.utils;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.extra.ftp.Ftp;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;

public class FaFtpUtils {

    public static Ftp connect(String host, int port, String username, String password) throws IOException {
        boolean secure = host.regionMatches(true, 0, "ftps://", 0, 7);
        String hostname = secure ? host.substring(7) : host;
        if (hostname.contains("://") || hostname.contains("/") || hostname.contains("@")) {
            throw new IOException("FTP 地址应填写主机名；显式 FTPS 使用 ftps://主机名，端口单独配置");
        }
        FTPClient client = secure ? new SessionReuseFTPSClient() : new FTPClient();
        connect(client, hostname, port, username, password);
        return new Ftp(client);
    }

    static void connect(FTPClient client, String host, int port, String username, String password) throws IOException {
        try {
            client.setConnectTimeout(30_000);
            client.setDefaultTimeout(30_000);
            client.setDataTimeout(Duration.ofSeconds(60));
            client.connect(host, port);
            client.setSoTimeout(30_000);
            checkReply(client, "连接");
            if (!client.login(username, password)) throw failure(client, "登录");
            if (client instanceof FTPSClient) {
                FTPSClient ftps = (FTPSClient) client;
                ftps.execPBSZ(0);
                ftps.execPROT("P");
                ftps.setEnabledSessionCreation(false);
            }
            client.enterLocalPassiveMode();
            if (!client.setFileType(FTP.BINARY_FILE_TYPE)) throw failure(client, "设置二进制传输");
        } catch (IOException | RuntimeException e) {
            try {
                client.disconnect();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    public static FTPFile[] listFiles(FTPClient client, String path) throws IOException {
        FTPFile[] files = client.listFiles(path);
        checkReply(client, "目录列表");
        return files;
    }

    /** 同步下载；仅在完整传输成功后替换缓存文件。 */
    public static void download(FTPClient client, String remotePath, Path target) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), ".ftp-", ".part");
        try {
            if (!client.setFileType(FTP.BINARY_FILE_TYPE)) throw failure(client, "设置二进制传输");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                if (!client.retrieveFile(remotePath, output)) throw failure(client, "下载");
                checkReply(client, "下载");
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException deleteError) {
                e.addSuppressed(deleteError);
            }
            throw e;
        }
    }

    private static void checkReply(FTPClient client, String action) throws IOException {
        if (!FTPReply.isPositiveCompletion(client.getReplyCode())) throw failure(client, action);
    }

    private static IOException failure(FTPClient client, String action) {
        int code = client.getReplyCode();
        String reason = code == 550 ? "文件不存在或不可访问" : code == 522 ? "TLS 数据连接失败，服务器要求会话复用" : "服务器拒绝请求";
        // 不拼接服务器原始响应，避免异常响应回显凭据。
        return new IOException("FTP " + action + "失败 [" + code + "]：" + reason);
    }

    /**
     * 获取最新的文件
     * @param files
     * @return
     */
    public static FTPFile getLatest(FTPFile[] files) {
        if (files.length == 0) return null;
        return ListUtil.sort(Arrays.asList(files), (o1, o2) -> -o1.getTimestamp().compareTo(o2.getTimestamp())).get(0);
    }

}
