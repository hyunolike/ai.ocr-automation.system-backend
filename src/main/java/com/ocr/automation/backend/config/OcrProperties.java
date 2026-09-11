package com.ocr.automation.backend.config;

import com.ocr.automation.backend.document.service.ProcessingProperties;
import com.ocr.automation.backend.ocr.OcrEngineProperties;
import com.ocr.automation.backend.storage.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@code ocr.*} 설정 바인딩을 한곳에서 켠다.
 * 설정 값의 실제 출처는 설정 서버(ocr-config-server)다.
 */
@Configuration
@EnableConfigurationProperties({
        StorageProperties.class,
        OcrEngineProperties.class,
        ProcessingProperties.class
})
public class OcrProperties {
}
