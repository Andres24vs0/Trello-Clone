package com.ingenieriadesoftware.EstoNoEsTrello.Acceptance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utilidad de evidencias para las Pruebas de Aceptación (sección 5.4 / 6.4
 * del Plan de Pruebas ST-KANBAN-PERF-PLAN).
 * <p>
 * Las pruebas de aceptación son, por naturaleza, "pruebas manuales guiadas"
 * (caja negra) que terminan en un checklist marcado como aceptado/rechazado
 * y, en el caso de CP-A-03, en un Acta de Aceptación firmada por el
 * cliente. Esta clase no reemplaza esa validación humana: solo genera el
 * respaldo objetivo (tiempos medidos, timestamps, resultado por paso) que
 * el Líder General adjunta al checklist/Acta como evidencia.
 * <p>
 * Todos los reportes se escriben en target/acceptance-reports/, por lo que
 * se regeneran en cada `mvn clean` y no ensucian el control de versiones.
 */
public final class AcceptanceEvidenceWriter {

    private static final Path REPORTS_DIR = Paths.get("target", "acceptance-reports");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Path reportFile;

    public AcceptanceEvidenceWriter(String reportFileName) {
        try {
            Files.createDirectories(REPORTS_DIR);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear " + REPORTS_DIR, e);
        }
        this.reportFile = REPORTS_DIR.resolve(reportFileName);
    }

    public Path getReportFile() {
        return reportFile;
    }

    public synchronized void line(String text) {
        String stamped = "[" + LocalDateTime.now().format(TS) + "] " + text;
        System.out.println(stamped);
        try {
            Files.writeString(reportFile, stamped + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir en " + reportFile, e);
        }
    }

    public void header(String title) {
        line("==================================================");
        line(title);
        line("==================================================");
    }

    public void step(String stepName, long elapsedMillis, long thresholdMillis) {
        boolean ok = elapsedMillis <= thresholdMillis;
        line(String.format("PASO [%s] -> %d ms (umbral %d ms) -> %s",
                stepName, elapsedMillis, thresholdMillis, ok ? "ACEPTADO" : "RECHAZADO"));
    }
}
