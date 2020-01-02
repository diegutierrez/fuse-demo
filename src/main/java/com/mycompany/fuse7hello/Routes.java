package com.mycompany.fuse7hello;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.language.Simple;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Routes extends RouteBuilder {

    @Value("${my.greeting}")
    private String greeting;

    @Override
    public void configure() throws Exception {

        restConfiguration("servlet")
            .bindingMode(RestBindingMode.json)
            .apiContextPath("/swagger") 
            .apiContextRouteId("swagger")

            .contextPath("/api")
            .apiProperty("api.title", "Example REST api")
            .apiProperty("api.version", "1.0")
            .apiProperty("host","")
        ;

        from("timer:myTimer?period=2000&repeatCount=3")
            .log("Hello World!");

        rest()
            .get("hello")
                .route()
                .setBody().body(()->new HelloResponse(greeting+" World!"))
                .removeHeaders("*")
                .endRest()
            .get("hello/{name}")
                .description("Hello with name")
                .param().name("name").type(RestParamType.path).description("Name of the user").required(true).dataType("string").endParam()
                .responseMessage().code(200).responseModel(HelloResponse.class).endResponseMessage() 

                .route()
                .setBody(method(this,"setHelloWithName"))
                .removeHeaders("*")
                .endRest()

            .get("memory/{mb}")
                .route()
                .setBody().method(this,"createObject")
                .endRest()
            .get("cpu/{iteration}")
                .route()
                .setBody().method(this,"cpuLoad")
                .endRest()
            .get("threads/{threads}/cpu/{iteration}")
                .route()
                .setBody().method(this,"multiThreadCpuLoad")
                .endRest()
        ;

    }

    public HelloResponse setHelloWithName(@Simple("${properties:my.greeting} ${header.name}!") String message){
        return new HelloResponse(message);
    }

    public Integer createObject(@Header("mb") Integer mb){
        byte[] one = new byte[mb*1024*1024];
        return mb;
    }

    public Long cpuLoad(@Header("iteration") Long iteration){
        double response =0.5;
        long start = System.currentTimeMillis();
        for (long i=0; i<iteration;i++){
            response = i%2==0 ? Math.pow(response,2.0) : Math.sqrt(response);
            if (i%(iteration/10) == 0) log.info("Iteration: {}",i);
        }
        long time = System.currentTimeMillis()-start;
        log.info("Time {}: {}",iteration,time);
        return time;
    }

    public Long multiThreadCpuLoad(@Header("threads") int threads, @Header("iteration") Long iteration) throws InterruptedException{
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        long start = System.currentTimeMillis();
        for (int i=0; i<threads; i++) {
            executor.submit(()->this.cpuLoad(iteration));
        }
        executor.shutdown();
        executor.awaitTermination(20, TimeUnit.MINUTES);

        long time = System.currentTimeMillis()-start;
        log.info("Total time {}/{}: {}",threads,iteration,time);
        return time;
    }
}
