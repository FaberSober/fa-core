package com.faber.core.exception.license;

import com.faber.core.constant.CommonConstants;
import com.faber.core.exception.BaseException;

/**
 * Raised when a request is blocked by the current license state.
 */
public class LicenseInvalidException extends BaseException {

    public LicenseInvalidException() {
        super("系统授权已失效，请联系服务提供商", CommonConstants.EX_LICENSE_INVALID_CODE);
    }
}
