package com.faber.core.license;

public class LicenseFileException extends RuntimeException {

    public LicenseFileException(String message) {
        super(message);
    }

    public LicenseFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
