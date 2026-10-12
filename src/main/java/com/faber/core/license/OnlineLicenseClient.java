package com.faber.core.license;

import com.dtflys.forest.annotation.JSONBody;
import com.dtflys.forest.annotation.Post;

public interface OnlineLicenseClient {

    @Post(url = "{0}/api/v1/license/validate", contentType = "application/json",
            timeout = 5000, connectTimeout = 2000, readTimeout = 5000, logEnabled = false)
    OnlineLicenseResponse validate(String serverUrl, @JSONBody OnlineLicenseRequest request);
}
