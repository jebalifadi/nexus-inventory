package com.nexus.core.system;

import java.util.List;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes basic service information, including the currencies configured in the database. */
@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    private final JdbcClient jdbcClient;
    private final BuildProperties build;

    public SystemInfoController(JdbcClient jdbcClient, BuildProperties build) {
        this.jdbcClient = jdbcClient;
        this.build = build;
    }

    @GetMapping("/info")
    public SystemInfo info() {
        List<String> currencies = jdbcClient
                .sql("SELECT code FROM currencies ORDER BY code")
                .query(String.class)
                .list();
        return new SystemInfo("Nexus", "core-service", build.getVersion(), currencies);
    }
}
