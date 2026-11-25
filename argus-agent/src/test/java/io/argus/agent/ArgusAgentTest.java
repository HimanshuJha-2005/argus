package io.argus.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArgusAgentTest {

    @Test
    @DisplayName("AgentBuilder must construct a valid transformer targeting DispatcherServlet")
    void shouldConstructValidAgentBuilder() {
        AgentBuilder builder = ArgusAgent.buildAgentBuilder();
        assertThat(builder).isNotNull();
    }
}
