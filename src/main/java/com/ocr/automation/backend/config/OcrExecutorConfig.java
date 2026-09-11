package com.ocr.automation.backend.config;

import com.ocr.automation.backend.document.service.ProcessingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * OCR 실행 워커 풀.
 *
 * <p>내부 API 는 작업을 이 풀에 넘기고 즉시 응답한다. OCR 을 HTTP 요청 안에서
 * 끝까지 하려 하면 호출자(스케줄러)의 읽기 타임아웃을 넘기고, 요청이 끊긴 뒤에도
 * 처리는 계속 돌아 다음 주기 요청과 겹친다.
 */
@Slf4j
@Configuration
public class OcrExecutorConfig {

    public static final String OCR_EXECUTOR = "ocrExecutor";

    @Bean(name = OCR_EXECUTOR, destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor ocrExecutor(ProcessingProperties properties) {
        int concurrency = properties.effectiveConcurrency();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        // 코어와 최대치를 같게 둔다. 큐가 차기 전에는 스레드가 늘지 않는 자바 풀 특성상
        // 둘을 다르게 두면 maxPoolSize 가 사실상 쓰이지 않아 오해만 낳는다.
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("ocr-worker-");

        // 바운드 큐 + AbortPolicy 가 백프레셔다. 큐가 차면 거부하고, 호출자는
        // 선점을 멈춘 뒤 다음 주기에 다시 시도한다.
        //
        // CallerRunsPolicy 를 쓰면 안 된다. 호출 스레드(= HTTP 요청 스레드)가 OCR 을
        // 대신 돌리게 되어, 비동기로 바꾼 이유였던 타임아웃 문제가 그대로 되살아난다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        // 종료 시 처리 중인 문서는 끝내고 나간다. 중간에 끊기면 PROCESSING 에 남아
        // 정체 회수 잡을 기다려야 한다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        log.info("OCR 워커 풀: concurrency={}, queueCapacity={}", concurrency, properties.queueCapacity());
        return executor;
    }
}
