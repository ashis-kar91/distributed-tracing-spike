package com.observability.spike.order.config;

import com.azure.monitor.opentelemetry.exporter.AzureMonitorExporterBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OpenTelemetryConfig {

    @Value("${spring.application.name:order-service}")
    private String serviceName;

    @Value("${APPLICATIONINSIGHTS_CONNECTION_STRING:#{null}}")
    private String connectionString;

    @Bean
    public OpenTelemetry openTelemetry() {
        Resource resource = Resource.getDefault().toBuilder()
                .put(AttributeKey.stringKey("service.name"), serviceName)
                .put(AttributeKey.stringKey("service.version"), "1.0.0")
                .put(AttributeKey.stringKey("deployment.environment"), "development")
                .build();

        var tracerProviderBuilder = SdkTracerProvider.builder()
                .setResource(resource);

        var meterProviderBuilder = SdkMeterProvider.builder()
                .setResource(resource);

        if (connectionString != null && !connectionString.isEmpty()) {
            // Azure Monitor Exporters
            var azureTraceExporter = new AzureMonitorExporterBuilder()
                    .connectionString(connectionString)
                    .buildTraceExporter();

            var azureMetricExporter = new AzureMonitorExporterBuilder()
                    .connectionString(connectionString)
                    .buildMetricExporter();

            tracerProviderBuilder.addSpanProcessor(BatchSpanProcessor.builder(azureTraceExporter).build());
            meterProviderBuilder.registerMetricReader(PeriodicMetricReader.builder(azureMetricExporter)
                    .setInterval(Duration.ofSeconds(30))
                    .build());
        }

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProviderBuilder.build())
                .setMeterProvider(meterProviderBuilder.build())
                .buildAndRegisterGlobal();
    }

    @Bean
    public Tracer otelTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName);
    }

    @Bean
    public Meter otelMeter(OpenTelemetry openTelemetry) {
        return openTelemetry.getMeter(serviceName);
    }
}
