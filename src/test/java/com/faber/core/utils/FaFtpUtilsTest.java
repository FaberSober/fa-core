package com.faber.core.utils;

import cn.hutool.extra.ftp.Ftp;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLHandshakeException;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FaFtpUtilsTest {

    @TempDir Path directory;

    @Test
    void plainFtpListsAndDownloadsTwoCompleteBinaryFiles() throws Exception {
        byte[] payload = new byte[]{0, 1, 10, 13, (byte) 255};
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5000);
            CompletableFuture<Void> serving = CompletableFuture.runAsync(() -> {
                try (Socket control = server.accept();
                     BufferedReader input = new BufferedReader(new InputStreamReader(control.getInputStream(), StandardCharsets.US_ASCII));
                     PrintWriter output = new PrintWriter(control.getOutputStream(), true, StandardCharsets.US_ASCII)) {
                    control.setSoTimeout(5000);
                    output.print("220 Ready\r\n"); output.flush();
                    ServerSocket data = null;
                    try {
                        String command;
                        while ((command = input.readLine()) != null) {
                            String verb = command.split(" ", 2)[0];
                            switch (verb) {
                                case "USER": output.print("331 Password required\r\n"); break;
                                case "PASS": output.print("230 Logged in\r\n"); break;
                                case "TYPE": assertEquals("TYPE I", command); output.print("200 Binary\r\n"); break;
                                case "SYST": output.print("215 UNIX Type: L8\r\n"); break;
                                case "PASV":
                                    data = new ServerSocket(0);
                                    data.setSoTimeout(5000);
                                    output.print("227 Passive (127,0,0,1," + data.getLocalPort() / 256 + "," + data.getLocalPort() % 256 + ")\r\n");
                                    break;
                                case "LIST":
                                case "RETR":
                                    output.print("150 Opening data connection\r\n"); output.flush();
                                    try (Socket socket = data.accept()) {
                                        socket.getOutputStream().write(verb.equals("LIST")
                                                ? "-rw-r--r-- 1 owner group 5 Oct 12 12:00 file.bin\r\n".getBytes(StandardCharsets.US_ASCII) : payload);
                                    }
                                    data.close(); data = null;
                                    output.print("226 Transfer complete\r\n");
                                    break;
                                case "QUIT": output.print("221 Goodbye\r\n"); output.flush(); return;
                                default: throw new AssertionError("Unexpected FTP command: " + verb);
                            }
                            output.flush();
                        }
                    } finally {
                        if (data != null) data.close();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            try (Ftp ftp = FaFtpUtils.connect("127.0.0.1", server.getLocalPort(), UUID.randomUUID().toString(), UUID.randomUUID().toString())) {
                assertFalse(ftp.getClient() instanceof FTPSClient);
                assertEquals(1, FaFtpUtils.listFiles(ftp.getClient(), "/").length);
                for (String filename : new String[]{"one.bin", "two.bin"}) {
                    Path target = directory.resolve(filename);
                    FaFtpUtils.download(ftp.getClient(), "/file.bin", target);
                    assertArrayEquals(payload, Files.readAllBytes(target));
                }
            }
            serving.get(5, TimeUnit.SECONDS);
            assertEquals(2, fileCount());
        }
    }

    @Test
    void ftpsLoginFailureDisconnects() throws Exception {
        FTPSClient client = mock(FTPSClient.class);
        when(client.getReplyCode()).thenReturn(220, 530);
        when(client.login(anyString(), anyString())).thenReturn(false);
        IOException error = assertThrows(IOException.class, () -> FaFtpUtils.connect(client, "localhost", 21,
                UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        assertTrue(error.getMessage().contains("530"));
        verify(client).disconnect();
        verify(client, never()).execPROT(anyString());
    }

    @Test
    void certificateFailureDisconnectsAndKeepsCause() throws Exception {
        FTPSClient client = mock(FTPSClient.class);
        SSLHandshakeException failure = new SSLHandshakeException("No subject alternative names matching IP address");
        doThrow(failure).when(client).connect("localhost", 21);
        assertSame(failure, assertThrows(IOException.class, () -> FaFtpUtils.connect(client, "localhost", 21,
                UUID.randomUUID().toString(), UUID.randomUUID().toString())));
        verify(client).disconnect();
    }

    @Test
    void ftpsUsesProtectedPassiveBinaryData() throws Exception {
        FTPSClient client = mock(FTPSClient.class);
        when(client.getReplyCode()).thenReturn(220);
        when(client.login(anyString(), anyString())).thenReturn(true);
        when(client.setFileType(2)).thenReturn(true);
        FaFtpUtils.connect(client, "localhost", 21, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        var order = inOrder(client);
        order.verify(client).execPBSZ(0);
        order.verify(client).execPROT("P");
        order.verify(client).setEnabledSessionCreation(false);
        order.verify(client).enterLocalPassiveMode();
        order.verify(client).setFileType(2);
    }

    @Test
    void downloadReturnsOnlyAfterTransferAndCompletionReply() throws Exception {
        FTPClient client = mock(FTPClient.class);
        when(client.setFileType(2)).thenReturn(true);
        when(client.getReplyCode()).thenReturn(226);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        when(client.retrieveFile(anyString(), any(OutputStream.class))).thenAnswer(invocation -> {
            OutputStream output = invocation.getArgument(1);
            output.write(1);
            started.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new IOException("Test transfer timed out");
            output.write(2);
            return true;
        });
        Path target = directory.resolve("complete.bin");
        CompletableFuture<Void> downloading = CompletableFuture.runAsync(() -> {
            try {
                FaFtpUtils.download(client, "/file", target);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertFalse(downloading.isDone());
            assertFalse(Files.exists(target));
        } finally {
            finish.countDown();
        }
        downloading.get(5, TimeUnit.SECONDS);
        assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(target));
        assertEquals(1, fileCount());
    }

    @Test
    void failedDownloadDeletesPartialAndPreservesOldCache() throws Exception {
        FTPClient client = mock(FTPClient.class);
        when(client.setFileType(2)).thenReturn(true);
        when(client.getReplyCode()).thenReturn(522);
        when(client.retrieveFile(anyString(), any(OutputStream.class))).thenAnswer(invocation -> {
            ((OutputStream) invocation.getArgument(1)).write(1);
            return false;
        });
        Path target = directory.resolve("cached.bin");
        Files.writeString(target, "old complete file");
        IOException error = assertThrows(IOException.class, () -> FaFtpUtils.download(client, "/file", target));
        assertTrue(error.getMessage().contains("522"));
        assertTrue(error.getMessage().contains("会话复用"));
        assertEquals("old complete file", Files.readString(target));
        assertEquals(1, fileCount());
    }

    @Test
    void missingFileAndConnectionErrorPropagateAndRemoveTemporaryFile() throws Exception {
        FTPClient client = mock(FTPClient.class);
        when(client.setFileType(2)).thenReturn(true);
        when(client.getReplyCode()).thenReturn(550);
        Path target = directory.resolve("missing.bin");
        IOException missing = assertThrows(IOException.class, () -> FaFtpUtils.download(client, "/missing", target));
        assertTrue(missing.getMessage().contains("550"));
        assertTrue(missing.getMessage().contains("文件不存在或不可访问"));
        assertEquals(0, fileCount());
        IOException disconnected = new IOException("Connection reset");
        when(client.retrieveFile(anyString(), any(OutputStream.class))).thenThrow(disconnected);
        assertSame(disconnected, assertThrows(IOException.class, () -> FaFtpUtils.download(client, "/file", target)));
        assertEquals(0, fileCount());
    }

    @Test
    void emptyListIsNotProofOfMissingFileAnd522IsReported() throws Exception {
        FTPClient client = mock(FTPClient.class);
        when(client.listFiles(anyString())).thenReturn(new FTPFile[0]);
        when(client.getReplyCode()).thenReturn(226);
        assertEquals(0, FaFtpUtils.listFiles(client, "/empty").length);
        when(client.getReplyCode()).thenReturn(522);
        assertTrue(assertThrows(IOException.class, () -> FaFtpUtils.listFiles(client, "/file")).getMessage().contains("522"));
        when(client.getReplyCode()).thenReturn(550);
        assertTrue(assertThrows(IOException.class, () -> FaFtpUtils.listFiles(client, "/file")).getMessage().contains("不可访问"));
    }

    private long fileCount() throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.count();
        }
    }
}
