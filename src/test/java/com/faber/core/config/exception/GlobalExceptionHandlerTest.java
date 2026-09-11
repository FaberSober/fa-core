package com.faber.core.config.exception;

import com.faber.core.exception.license.LicenseInvalidException;
import com.faber.core.vo.msg.BaseRet;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void mapsLicenseInvalidExceptionToStableErrorCode() {
        BaseRet result = new GlobalExceptionHandler().baseExceptionHandler(
                new MockHttpServletResponse(), new LicenseInvalidException());

        assertEquals(40303, result.getCode());
        assertEquals(40303, result.getStatus());
        assertEquals("系统授权已失效，请联系服务提供商", result.getMessage());
    }
}
