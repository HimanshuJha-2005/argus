package io.argus.agent;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

/**
 * ByteBuddy advice applied to Spring's DispatcherServlet.doDispatch.
 * Extracts Route Templates, Path, and Status codes with zero overhead.
 */
public final class DispatcherServletAdvice {

    public static final String BEST_MATCHING_PATTERN_ATTR =
            "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern";

    private DispatcherServletAdvice() {}

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long onEnter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.Enter long startNanos,
            @Advice.Argument(0) HttpServletRequest request,
            @Advice.Argument(1) HttpServletResponse response
    ) {
        if (request == null || response == null) {
            return;
        }

        long durationNanos = Math.max(0L, System.nanoTime() - startNanos);
        String method = request.getMethod();
        String path = request.getRequestURI();

        Object patternAttr = request.getAttribute(BEST_MATCHING_PATTERN_ATTR);
        String routeTemplate = (patternAttr != null && !patternAttr.toString().isBlank())
                ? patternAttr.toString()
                : (path != null ? path : "/unknown");

        int status = response.getStatus();

        SensorBridge.emitTransaction(method, routeTemplate, path, status, durationNanos);
    }
}
