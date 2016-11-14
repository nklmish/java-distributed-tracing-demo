package com.nklmish.cs.catalogs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.async.TraceableExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

@SpringBootApplication
@EnableAsync
@EnableHystrix
public class Application {

    private final Tracer tracer;

    @Autowired
    public Application(Tracer tracer) {
        this.tracer = tracer;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public TraceableExecutorService executorService() {
        return new TraceableExecutorService(
                newSingleThreadExecutor(),
                tracer,
                new TraceKeys(),
                new DefaultSpanNamer()
        );
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
