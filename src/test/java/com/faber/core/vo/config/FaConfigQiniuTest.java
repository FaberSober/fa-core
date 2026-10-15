package com.faber.core.vo.config;

import com.faber.core.enums.ConfigSysQiniuAccessTypeEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FaConfigQiniuTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultsMissingAndNullAccessTypeToPublic() throws Exception {
        assertEquals(ConfigSysQiniuAccessTypeEnum.PUBLIC, new FaConfig().getQiniuAccessType());
        assertEquals(ConfigSysQiniuAccessTypeEnum.PUBLIC, mapper.readValue("{}", FaConfig.class).getQiniuAccessType());
        FaConfig config = mapper.readValue("{\"qiniuAccessType\":null}", FaConfig.class);
        assertEquals(ConfigSysQiniuAccessTypeEnum.PUBLIC, config.getQiniuAccessType());
        assertEquals("PUBLIC", mapper.readTree(mapper.writeValueAsString(config)).get("qiniuAccessType").asText());
    }

    @Test
    void preservesBothAccessTypesAcrossJsonRoundTrip() throws Exception {
        for (ConfigSysQiniuAccessTypeEnum accessType : ConfigSysQiniuAccessTypeEnum.values()) {
            FaConfig config = new FaConfig();
            config.setQiniuAccessType(accessType);
            String json = mapper.writeValueAsString(config);
            assertEquals(accessType.getValue(), mapper.readTree(json).get("qiniuAccessType").asText());
            assertEquals(accessType, mapper.readValue(json, FaConfig.class).getQiniuAccessType());
        }
    }

    @Test
    void rejectsUnknownAndOrdinalAccessTypes() {
        for (String value : new String[]{"\"OTHER\"", "\"private\"", "\"\"", "0", "1", "true", "{}", "[]"}) {
            assertThrows(JsonProcessingException.class,
                    () -> mapper.readValue("{\"qiniuAccessType\":" + value + "}", FaConfig.class), value);
        }
    }
}
