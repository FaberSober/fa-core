package com.faber.core.license;

import lombok.Data;

@Data
public class OnlineLicenseResponse {

    private String code;
    private String message;
    private LicenseInfo license;
}
