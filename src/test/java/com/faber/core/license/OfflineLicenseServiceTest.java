package com.faber.core.license;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfflineLicenseServiceTest {

    @Test
    void storesOnlyAnActiveCandidate() {
        LicenseProperties properties = new LicenseProperties();
        properties.setMode(LicenseMode.OFFLINE);
        properties.setLicenseFile("/tmp/license.lic");
        LicenseFileCodec codec = Mockito.mock(LicenseFileCodec.class);
        LicenseManager manager = Mockito.mock(LicenseManager.class);
        OfflineLicenseStore store = Mockito.mock(OfflineLicenseStore.class);
        LicenseInfo candidate = new LicenseInfo();
        byte[] content = "license".getBytes(StandardCharsets.UTF_8);
        when(codec.read(content)).thenReturn(candidate);
        when(manager.validateCandidate(candidate)).thenReturn(LicenseState.ACTIVE);
        when(manager.refresh()).thenReturn(LicenseState.ACTIVE);

        OfflineLicenseService service = new OfflineLicenseService(properties, codec, manager, store);

        assertEquals(LicenseState.ACTIVE, service.importLicense("license.lic", content));
        verify(store).replace(properties.getLicenseFile(), content);
    }

    @Test
    void doesNotReplaceFileForInvalidCandidate() {
        LicenseProperties properties = new LicenseProperties();
        properties.setMode(LicenseMode.OFFLINE);
        properties.setLicenseFile("/tmp/license.lic");
        LicenseFileCodec codec = Mockito.mock(LicenseFileCodec.class);
        LicenseManager manager = Mockito.mock(LicenseManager.class);
        OfflineLicenseStore store = Mockito.mock(OfflineLicenseStore.class);
        LicenseInfo candidate = new LicenseInfo();
        byte[] content = "license".getBytes(StandardCharsets.UTF_8);
        when(codec.read(content)).thenReturn(candidate);
        when(manager.validateCandidate(candidate)).thenReturn(LicenseState.MACHINE_MISMATCH);

        OfflineLicenseService service = new OfflineLicenseService(properties, codec, manager, store);

        assertThrows(LicenseFileException.class, () -> service.importLicense("license.lic", content));
        verify(store, never()).replace(any(), any());
    }
}
