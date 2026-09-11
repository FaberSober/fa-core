package com.faber.core.license;

import com.dtflys.forest.exceptions.ForestNetworkException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OnlineLicenseProvider implements LicenseProvider {

    private final LicenseProperties properties;
    private final MachineIdProvider machineIdProvider;
    private final OnlineLicenseClient client;
    private final OnlineLicenseCache cache;

    public OnlineLicenseProvider(LicenseProperties properties,
                                 MachineIdProvider machineIdProvider,
                                 OnlineLicenseClient client,
                                 OnlineLicenseCache cache) {
        this.properties = properties;
        this.machineIdProvider = machineIdProvider;
        this.client = client;
        this.cache = cache;
    }

    @Override
    public boolean supports(LicenseMode mode) {
        return mode == LicenseMode.ONLINE;
    }

    @Override
    public Optional<LicenseInfo> load() {
        if (properties.getServerUrl() == null || properties.getServerUrl().isBlank()
                || properties.getLicenseKey() == null || properties.getLicenseKey().isBlank()) {
            return Optional.empty();
        }

        OnlineLicenseRequest request = new OnlineLicenseRequest(
                properties.getLicenseKey(),
                properties.getProduct(),
                machineIdProvider.getMachineId(),
                properties.getClientVersion());
        OnlineLicenseResponse response;
        try {
            response = client.validate(properties.getServerUrl(), request);
        } catch (ForestNetworkException e) {
            if (e.getStatusCode() != null && e.getStatusCode() < 500) {
                throw new LicenseFileException("授权中心拒绝 License", e);
            }
            return cachedOrThrow(e);
        } catch (Exception e) {
            return cachedOrThrow(e);
        }
        if (response == null || response.getLicense() == null) {
            throw new LicenseFileException("授权中心响应无有效 License");
        }
        if (!"OK".equalsIgnoreCase(response.getCode())) {
            throw new LicenseFileException("授权中心拒绝 License");
        }
        return Optional.of(response.getLicense());
    }

    @Override
    public void onAccepted(LicenseInfo licenseInfo) {
        cache.save(licenseInfo);
    }

    private Optional<LicenseInfo> cachedOrThrow(Exception cause) {
        Optional<LicenseInfo> cached = cache.load();
        if (cached.isPresent()) {
            return cached;
        }
        throw new OnlineLicenseException("授权中心暂时不可用，且没有可用缓存", cause);
    }
}
