package io.micrometer.core.instrument.binder.tomcat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jetty.JettyStatisticsMetrics;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.catalina.Context;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.session.TooManyActiveSessionsException;
import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static io.micrometer.core.instrument.MockClock.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("ConstantConditions")
class TomcatMetricsTest {
    private SimpleMeterRegistry registry;
    private ManagerBase manager;

    @BeforeEach
    void setup() throws SQLException {
        this.registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        Context context =  new StandardContext();//mock(Context.class);

        this.manager = new ManagerBase() {
            @Override
            public void load() throws ClassNotFoundException, IOException {

            }

            @Override
            public void unload() throws IOException {

            }

            @Override
            public Context getContext() {
                return context;
            }
        };
        manager.setMaxActiveSessions(3);
    }

    @Test
    void stats() throws IOException, ServletException {
        manager.createSession("first");
        manager.createSession("second");
        Session thirdSession = manager.createSession("third");
        try{manager.createSession("forth");} catch(TooManyActiveSessionsException exception) {
            //ignore error, testing rejection
        }
        StandardSession expiredSession = new StandardSession(manager);
        expiredSession.setId("third");
        expiredSession.setCreationTime(System.currentTimeMillis()- 10_000);
        manager.remove(expiredSession, true);

        List<Tag> tags = Tags.zip("metricTag", "val1");
        TomcatMetrics.monitor(registry, manager, tags);

        assertThat(registry.find("tomcat.sessions.active.max").tags(tags).gauge().map(Gauge::value)).hasValue(3.0);
        assertThat(registry.find("tomcat.sessions.active.current").tags(tags).gauge().map(Gauge::value)).hasValue(2.0);
        assertThat(registry.find("tomcat.sessions.expired").tags(tags).gauge().map(Gauge::value)).hasValue(1.0);
        assertThat(registry.find("tomcat.sessions.rejected").tags(tags).gauge().map(Gauge::value)).hasValue(1.0);
        assertThat(registry.find("tomcat.sessions.created").tags(tags).functionCounter().map(FunctionCounter::count)).hasValue(3.0);
        assertThat(registry.find("tomcat.sessions.alive.max").tags(tags).gauge().map(Gauge::value).get()).isGreaterThan(1.0);
    }

}
