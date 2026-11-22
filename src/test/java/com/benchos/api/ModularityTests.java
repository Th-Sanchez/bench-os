package com.benchos.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void verifiesModularStructure() {
        ApplicationModules modules = ApplicationModules.of(BenchOsApplication.class);
        modules.verify();
    }
}
