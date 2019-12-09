package com.arin.pang.okhttp.preloader;


import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.subjects.PublishSubject;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.platform.Platform;

public class HTTPPreloaderInterceptor implements Interceptor {


    private volatile LogLevel logLevel = LogLevel.NONE;
    private Map<Request, Request> requestMap = new ConcurrentHashMap<>();
    private PublishSubject<PreloaderInfo> publishCallCount;
    private Logger logger = new Logger() {
    };


    public HTTPPreloaderInterceptor(PublishSubject<PreloaderInfo> publishCallCount) {
        this.publishCallCount = publishCallCount;
    }


    public HTTPPreloaderInterceptor(PublishSubject<PreloaderInfo> publishCallCount, Logger logger) {
        this(publishCallCount);
        this.logger = logger;
    }

    public void logLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        boolean logRemainedRequest = (logLevel == LogLevel.REMAINED_REQUESTS);
        boolean logModifiedRequest = logRemainedRequest || (logLevel == LogLevel.MODIFIED_REQUEST);
        boolean logCallCount = logModifiedRequest || (logLevel == LogLevel.REMAINED_REQUESTS_COUNT);

        requestMap.put(chain.request(), chain.request());

        PreloaderRequest preReq = new PreloaderRequest(requestMap);
        publishCallCount.onNext(preReq);
        if (logCallCount) {
            logger.log("GET PROGRESSING REQUESTS " + preReq.getCount());
            if (logModifiedRequest) {
                logger.log("NEW Request INFO " + chain.request().toString());
            }
            if (logRemainedRequest) {
                for (Request key : preReq.getRequestMap().keySet()) {
                    logger.log("ALL REQUESTS INFO " + key.toString());
                }
            }
        }
        try {
            Response originalResponse = chain.proceed(chain.request());
            PreloaderResponse preRes = new PreloaderResponse(requestMap, originalResponse);
            publishCallCount.onNext(preRes);

            if (logCallCount) {
                logger.log("GET REMAINED REQUESTS " + preRes.getCount());
                if (logModifiedRequest) {
                    logger.log("NEW Response INFO " + originalResponse.toString());
                }
                if (logRemainedRequest) {
                    for (Request key : preRes.getRequestMap().keySet()) {
                        logger.log("REMAINED REQUESTS INFO " + key.toString());
                    }
                }
            }
            return originalResponse;

        } catch (IOException e) {
            PreloaderResponse errorRes = new PreloaderResponse(requestMap, chain.request(), e);
            publishCallCount.onNext(errorRes);

            if (logCallCount) {
                logger.log("Timeout Error. GET REMAINED REQUESTS " + errorRes.getCount());
                if (logModifiedRequest) {
                    logger.log("Timeout Request INFO " + chain.request().toString());
                }
                if (logRemainedRequest) {
                    for (Request key : errorRes.getRequestMap().keySet()) {
                        logger.log("REMAINED REQUESTS INFO " + key.toString());
                    }
                }
            }
            throw e;
        }
    }


    public enum LogLevel {
        NONE, REMAINED_REQUESTS_COUNT, MODIFIED_REQUEST, REMAINED_REQUESTS
    }

    interface Logger {
        default void log(String message) {
            Platform.get().log(Platform.INFO, message, null);
        }
    }

    public class PreloaderInfo {

        protected final Map<Request, Request> requestMap;

        PreloaderInfo(Map<Request, Request> requestMap) {
            this.requestMap = requestMap;
        }

        public boolean isRemained() {
            return !requestMap.isEmpty();
        }

        public Map<Request, Request> getRequestMap() {
            return requestMap;
        }

        public Integer getCount() {
            return requestMap.size();
        }
    }

    public class PreloaderRequest extends PreloaderInfo {
        PreloaderRequest(Map<Request, Request> requestMap) {
            super(requestMap);
        }

        public boolean isRemained() {
            return super.isRemained();
        }

        public Map<Request, Request> getRequestMap() {
            return super.getRequestMap();
        }

        public Integer getCount() {
            return super.getCount();
        }
    }

    public class PreloaderResponse extends PreloaderInfo {
        private final Exception error;
        private final Response latestResponse;


        PreloaderResponse(Map<Request, Request> requestMap, Response response) {
            super(requestMap);
            this.requestMap.remove(response.request());
            this.latestResponse = response;
            this.error = null;
        }

        PreloaderResponse(Map<Request, Request> requestMap, Request request, Exception e) {
            super(requestMap);
            this.requestMap.remove(request);
            this.latestResponse = null;
            this.error = e;
        }

        public boolean isRemained() {
            return super.isRemained();
        }

        public Map<Request, Request> getRequestMap() {
            return super.getRequestMap();
        }

        public Integer getCount() {
            return super.getCount();
        }

        public Response getLatestResponse() {
            return latestResponse;
        }
    }
}
