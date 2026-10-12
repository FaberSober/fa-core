package com.faber.core.license;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlineLicenseRequest {

    private String licenseKey;
    private String product;
    private String machineId;
    private String clientVersion;
}
