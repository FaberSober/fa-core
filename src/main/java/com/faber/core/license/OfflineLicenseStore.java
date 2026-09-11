package com.faber.core.license;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class OfflineLicenseStore {

    // ponytail: 单实例锁；集群并发导入时再升级为分布式协调。
    private final ReentrantLock replaceLock = new ReentrantLock();

    public void replace(String configuredPath, byte[] content) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new LicenseFileException("未配置 License 文件路径");
        }
        if (!configuredPath.toLowerCase(Locale.ROOT).endsWith(".lic")) {
            throw new LicenseFileException("License 文件必须使用 .lic 扩展名");
        }
        if (content == null || content.length == 0) {
            throw new LicenseFileException("License 文件为空");
        }

        Path target = Path.of(configuredPath).toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || target.getFileName() == null) {
            throw new LicenseFileException("License 文件路径无效");
        }

        replaceLock.lock();
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
        } catch (AtomicMoveNotSupportedException e) {
            throw new LicenseFileException("当前文件系统不支持安全替换 License 文件", e);
        } catch (IOException e) {
            throw new LicenseFileException("保存 License 文件失败", e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // 临时文件清理失败不覆盖原始保存错误。
                }
            }
            replaceLock.unlock();
        }
    }
}
