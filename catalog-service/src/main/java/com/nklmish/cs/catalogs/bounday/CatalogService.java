package com.nklmish.cs.catalogs.bounday;

import com.nklmish.cs.catalogs.model.Catalog;
import com.nklmish.cs.catalogs.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.async.TraceableExecutorService;
import org.springframework.cloud.sleuth.instrument.hystrix.TraceCommand;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.netflix.hystrix.HystrixCommand.Setter.withGroupKey;
import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@Slf4j
public class CatalogService implements ApplicationListener<ContextClosedEvent>{

    private final RestTemplate restTemplate;

    private final CommunicationService communicationService;

    private final TraceableExecutorService executorService;

    private final Tracer tracer;

    @Autowired
    public CatalogService(RestTemplate restTemplate,
                          CommunicationService communicationService,
                          TraceableExecutorService executorService, Tracer tracer) {
        this.restTemplate = restTemplate;
        this.communicationService = communicationService;
        this.executorService = executorService;
        this.tracer = tracer;
    }

    @RequestMapping(value = "/{id}", produces = APPLICATION_JSON_VALUE)
    public Catalog createCatalog(@PathVariable Integer id) throws InterruptedException, ExecutionException {
        tracer.getCurrentSpan().logEvent("price.fetch");
        String price = getPrice(id);

        tracer.getCurrentSpan().logEvent("products.fetch");
        List<Product> products = getProducts(id);

        tracer.getCurrentSpan().logEvent("email.send");
        communicationService.executeInBackground();

        simulatePause();

        executorService.execute(new Processor());
        return new Catalog(id, price, products);
    }

    private String getPrice(@PathVariable final Integer id) {
        return new TraceCommand<String>(
                tracer, new TraceKeys(),
                withGroupKey(asKey("price"))
                        .andCommandKey(() -> "calling-price-service")
                        .andThreadPoolKey(() -> "pricing")) {
            @Override
            public String doRun() throws Exception {
                return fetchPrice(id);
            }
        }.execute();
    }

    private String fetchPrice(Integer id) {
        return restTemplate.
                getForObject(
                        "http://localhost:8070/{id}",
                        String.class,
                        id);
    }

    private List<Product> getProducts(@PathVariable final Integer id) {
        return new TraceCommand<List<Product>>(
                tracer, new TraceKeys(),
                withGroupKey(asKey("product"))
                        .andCommandKey(() -> "calling-product-service")
                        .andThreadPoolKey(() -> "products")) {
            @Override
            public List<Product> doRun() throws Exception {
                return fetchProducts(id);
            }
        }.execute();
    }


    private List<Product> fetchProducts(Integer id) {
        return Arrays.asList(
                restTemplate.
                        getForObject(
                                "http://localhost:8090/{id}",
                                Product[].class,
                                id
                        )
        );
    }

    private void simulatePause() throws InterruptedException {
        //random sleep
        if (Math.random() > 0.5) {
            //report full GC
            TimeUnit.SECONDS.sleep(3);
            tracer.getCurrentSpan().logEvent("gc.full");
        }
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent contextClosedEvent) {
        executorService.shutdown();
    }
}

@Component
@Slf4j
class CommunicationService {
    private final Tracer tracer;

    @Autowired
    CommunicationService(Tracer tracer) {
        this.tracer = tracer;
    }

    @Async
    void executeInBackground() throws InterruptedException {
        log.info("sending email...");
        TimeUnit.SECONDS.sleep(1);
        tracer.addTag("cluster", "phoenix");
        log.info("Email sent");
    }
}

@SpanName("catalog-processor")
@Slf4j
class Processor implements Runnable {

    public void run() {
        log.info("Processing ...");
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException e) {
            log.error("sleep interrupted", e);
        }
        log.info("Processed");
    }
}
