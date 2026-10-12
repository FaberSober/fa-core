package com.faber.core.license;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LicenseSchedulerTest {

    @Test
    void refreshesOnlyEnabledOnlineMode() {
        LicenseProperties properties = new LicenseProperties();
        properties.setMode(LicenseMode.ONLINE);
        LicenseManager manager = mock(LicenseManager.class);

        new LicenseScheduler(properties, manager).refresh();

        verify(manager).refresh();
    }

    @Test
    void skipsDisabledAndOfflineMode() {
        LicenseProperties properties = new LicenseProperties();
        LicenseManager manager = mock(LicenseManager.class);
        LicenseScheduler scheduler = new LicenseScheduler(properties, manager);

        properties.setEnabled(false);
        scheduler.refresh();
        properties.setEnabled(true);
        properties.setMode(LicenseMode.OFFLINE);
        scheduler.refresh();

        verify(manager, never()).refresh();
    }
}
