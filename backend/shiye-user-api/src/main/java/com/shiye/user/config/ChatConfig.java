package com.shiye.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 流式问答线程池。
 *
 * <p>每个流式回答会占用一个线程直到模型结束，池子太小会让并发请求排队。
 * 默认值适合单机小规模使用，生产可以通过环境变量调整。</p>
 */
@Configuration
public class ChatConfig {

    @Bean(name = "chatExecutor")
    public ThreadPoolTaskExecutor chatExecutor(
            @Value("${shiye.chat.executor.core-size:16}") int coreSize,
            @Value("${shiye.chat.executor.max-size:32}") int maxSize,
            @Value("${shiye.chat.executor.queue-capacity:100}") int queueCapacity) {
        int core = Math.max(1, coreSize);
        int max = Math.max(core, maxSize);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setThreadNamePrefix("shiye-chat-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
