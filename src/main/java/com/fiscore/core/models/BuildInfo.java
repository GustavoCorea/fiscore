package com.fiscore.core.models;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:META-INF/build-info.properties")
public class BuildInfo {

    @Value("${build.version:No version available}")
    private String version;

    @Value("${build.time:No build time available}")
    private String buildTime;

    @Value("${build.name:No build number available}")//
    private String buildNumber; // Nuevo campo para el número de compilación

    public String getVersion() {
        return version;
    }

    public String getBuildTime() {
        return buildTime;
    }
    public String getBuildNumber() {
        return buildNumber; // Método getter para el número de compilación
    }

    /*@PostConstruct
    public void init() {
        System.out.println("Build Version: " + version);
        System.out.println("Build Time: " + buildTime);
    }*/

}
