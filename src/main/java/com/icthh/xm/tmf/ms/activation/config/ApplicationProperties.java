package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.commons.lep.TenantScriptStorage;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;

/**
 * Properties specific to Activation.
 * <p>
 * Properties are configured in the application.yml file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {
    private boolean schedulerEnabled;
    private boolean kafkaEnabled;
    private String kafkaSystemTopic;
    private String kafkaSystemQueue;
    private boolean timelinesEnabled;
    private String dbSchemaSuffix;
    private List<String> tenantWithCreationAccessList;
    private int kafkaConcurrencyCount;
    private int kafkaOffsetsMetricTimeout;
    private Long kafkaOffsetCacheTTL;
    private boolean kafkaOffsetsMetricEnabled = true;
    private RestTemplateProperties loadBalancedRestTemplate = new RestTemplateProperties();

    private final Lep lep = new Lep();
    private final Retry retry = new Retry();
    private final KafkaEventSender kafkaEventSender = new KafkaEventSender();
    private final KafkaMetric kafkaMetric = new KafkaMetric();
    private String configPrivateKey;
    private final ApiDocs apiDocs = new ApiDocs();
    private final Prometheus prometheus = new Prometheus();
    private final Management management = new Management();
    private final TenantCache tenantCache = new TenantCache();
    private final TenantMemoryCache tenantMemoryCache = new TenantMemoryCache();
    private final XmAutoConfigurationFilter xmAutoConfigurationFilter = new XmAutoConfigurationFilter();

    private List<String> tenantIgnoredPathList = Collections.emptyList();

    /**
     * Request headers that are not copied into the saga transaction context (case-insensitive).
     */
    private List<String> sagaContextExcludedHeaders = List.of("authorization", "cookie");

    @Getter
    @Setter
    public static class Lep {
        private TenantScriptStorage tenantScriptStorage;
        private String lepResourcePathPattern;
        private Boolean fullRecompileOnLepUpdate;
    }

    private int retryThreadCount;

    @Getter
    @Setter
    private static class KafkaEventSender {
        private Retry retry;
    }

    @Getter
    @Setter
    private static class Retry {
        private int maxAttempts;
        private long delay;
        private int multiplier;
    }

    @Getter
    @Setter
    public static class RestTemplateProperties {
        private int connectionRequestTimeout;
        private int connectTimeout;
        private int readTimeout;
    }

    @Getter
    @Setter
    public static class KafkaMetric {
        private boolean enabled;
    }

    @Getter
    @Setter
    public static class ApiDocs {
        private boolean privilegeCheck;
    }

    public static class Prometheus {
        private boolean publicAccess;

        public boolean isPublic() {
            return publicAccess;
        }

        public void setPublic(boolean publicAccess) {
            this.publicAccess = publicAccess;
        }
    }

    @Getter
    @Setter
    public static class TenantCache {
        private String enabled;
    }

    @Getter
    @Setter
    public static class TenantMemoryCache {
        private String enabled;
    }

    @Getter
    @Setter
    public static class XmAutoConfigurationFilter {
        private boolean disabled;
    }

    @Getter
    @Setter
    public static class Management {
        private final Access access = new Access();
        private final Info info = new Info();
        private final Logs logs = new Logs();

        @Getter
        @Setter
        public static class Access {
            private String role;
        }

        public static class Info {
            private boolean publicAccess;

            public boolean isPublic() {
                return publicAccess;
            }

            public void setPublic(boolean publicAccess) {
                this.publicAccess = publicAccess;
            }
        }

        @Getter
        @Setter
        public static class Logs {
            private boolean systemAdminOnly;
        }
    }
}
