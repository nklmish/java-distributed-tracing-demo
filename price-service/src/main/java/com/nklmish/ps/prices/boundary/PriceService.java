package com.nklmish.ps.prices.boundary;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.SpanAccessor;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
public class PriceService {

    private Map<Integer, String> cache;

    private SpanAccessor spanAccessor;

    private Tracer tracer;

    @Autowired
    public PriceService(SpanAccessor spanAccessor, Tracer tracer) {
        this.cache = new ConcurrentHashMap<>();
        this.spanAccessor = spanAccessor;
        this.tracer = tracer;
    }

    @PostConstruct
    public void populateCache() {
        this.cache.put(1, "100");
    }

    @RequestMapping(value = "/{id}", produces = APPLICATION_JSON_VALUE)
    public String calculateCost(@PathVariable Integer id) throws InterruptedException {
        String cachedPrice = cache.get(id);

        if (cachedPrice != null)
            return cachedPrice;

        tracer.addTag(
                "cluster",
                id < 100 ? "cluster1" : "cluster2"
        );

        spanAccessor.getCurrentSpan().logEvent("cache.miss");

        int sleepDuration = 30;
        TimeUnit.MILLISECONDS.sleep(sleepDuration);
        tracer.addTag("calculation.time", String.valueOf(sleepDuration) + " s");

        return String.valueOf(ThreadLocalRandom.current().nextInt(100, 1000 + 1));
    }
}
