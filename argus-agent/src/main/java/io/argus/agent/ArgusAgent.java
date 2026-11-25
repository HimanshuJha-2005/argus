package io.argus.agent;

import io.argus.core.ringbuffer.TelemetryRingBuffer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Java Agent entry point for Argus in-process runtime sensor.
 * Attached via -javaagent:argus-agent.jar at JVM boot.
 */
public final class ArgusAgent {

    public static final int DEFAULT_RING_BUFFER_CAPACITY = 16384;

    private ArgusAgent() {}

    public static void premain(String agentArgs, Instrumentation inst) {
        // 1. Initialize lock-free ring buffer for captured transactions
        TelemetryRingBuffer<CapturedTransaction> ringBuffer =
                TelemetryRingBuffer.create(DEFAULT_RING_BUFFER_CAPACITY);
        SensorBridge.registerRingBuffer(ringBuffer);

        // 2. Attach ByteBuddy transformation
        buildAgentBuilder().installOn(inst);

        System.out.println("[Argus] Runtime sensor initialized with capacity " + DEFAULT_RING_BUFFER_CAPACITY);
    }

    public static AgentBuilder buildAgentBuilder() {
        return new AgentBuilder.Default()
                .type(named("org.springframework.web.servlet.DispatcherServlet"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.method(named("doDispatch").and(takesArguments(2)))
                               .intercept(Advice.to(DispatcherServletAdvice.class))
                );
    }
}
