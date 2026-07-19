package com.ingenieriadesoftware.EstoNoEsTrello.Acceptance;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-A-01 — Flujo completo: iniciar sesión -> consultar espacio de trabajo
 * -> crear tarjeta.
 * <p>
 * Plan de Pruebas ST-KANBAN-PERF-PLAN, secciones 5.4 / 6.4, y
 * Especificación de Pruebas, tabla CP-A-01.
 * <p>
 * Técnica indicada por el plan: "prueba manual guiada con checklist de
 * aceptación", ejecutada en un navegador de escritorio soportado (Chrome,
 * Firefox, Edge u Opera GX) con cronómetro. Esta clase automatiza esa
 * misma sesión con Selenium WebDriver conduciendo un Chrome real (no
 * headless, para que la medición incluya renderizado igual que la
 * percepción humana), de forma que el tiempo de cada paso quede
 * registrado de forma objetiva y repetible como EVIDENCIA del checklist,
 * en lugar de depender únicamente de un cronómetro manual.
 * <p>
 * La validación final de "aceptado/rechazado" para efectos de la sección
 * 9 del Plan sigue siendo responsabilidad del Líder General junto al
 * cliente (ver Acta de Aceptación); esta prueba provee el dato objetivo
 * de tiempos que respalda esa decisión.
 * <p>
 * Precondición de datos: usa el usuario y workspace ya sembrados en
 * src/main/resources/JSONs/Users.json (volumen nominal, tal como exige la
 * especificación): albertrodri2710@gmail.com / 123 / "WorkSpace 1".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CP_A01_UserFlowPerceivedLatencyTest {

    // Umbral de aceptación acordado con el cliente (sección 8 del Plan de Pruebas)
    private static final long UMBRAL_MS = 2000L;

    // Datos de prueba nominales (ya existentes en Users.json)
    private static final String EMAIL = "albertrodri2710@gmail.com";
    private static final String PASSWORD = "123";
    private static final String WORKSPACE_NAME = "WorkSpace 1";

    @LocalServerPort
    private int port;

    private WebDriver driver;
    private AcceptanceEvidenceWriter evidence;

    @BeforeAll
    static void setupDriverManager() {
        WebDriverManager.chromedriver().setup();
    }

    @BeforeEach
    void setupDriver() {
        ChromeOptions options = new ChromeOptions();
        // NO headless a propósito: se quiere medir la percepción real del
        // usuario en un navegador de escritorio, tal como pide el ERS 2.2.1.
        // Si se ejecuta en un agente CI sin entorno gráfico, descomentar:
        // options.addArguments("--headless=new");
        options.addArguments("--window-size=1366,768");
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
        evidence = new AcceptanceEvidenceWriter("CP-A-01-checklist.txt");
    }

    @AfterEach
    void teardownDriver() {
        if (driver != null) {
            driver.quit();
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @DisplayName("CP-A-01: login -> consultar workspace -> crear tarjeta, cada paso <= 2000 ms")
    void flujoCompleto_ningunPasoSuperaElUmbralDeDosSegundos() {
        evidence.header("CP-A-01 - Flujo completo iniciar sesion -> consultar workspace -> crear tarjeta");
        evidence.line("Navegador: " + driver.toString());
        evidence.line("URL base: " + baseUrl());

        long tLogin = medirLogin();
        long tWorkspace = medirConsultaWorkspace();
        long tCard = medirCreacionTarjeta();

        evidence.step("Iniciar sesion", tLogin, UMBRAL_MS);
        evidence.step("Consultar espacio de trabajo", tWorkspace, UMBRAL_MS);
        evidence.step("Crear tarjeta", tCard, UMBRAL_MS);

        boolean checklistAceptado = tLogin <= UMBRAL_MS && tWorkspace <= UMBRAL_MS && tCard <= UMBRAL_MS;
        evidence.line("Checklist de aceptacion CP-A-01: " + (checklistAceptado ? "ACEPTADO" : "RECHAZADO"));
        evidence.line("Evidencia completa en: " + evidence.getReportFile().toAbsolutePath());

        assertTrue(tLogin <= UMBRAL_MS, "Paso 'Iniciar sesion' supero los " + UMBRAL_MS + " ms");
        assertTrue(tWorkspace <= UMBRAL_MS, "Paso 'Consultar workspace' supero los " + UMBRAL_MS + " ms");
        assertTrue(tCard <= UMBRAL_MS, "Paso 'Crear tarjeta' supero los " + UMBRAL_MS + " ms");
    }

    /** Paso 1: iniciar sesión (login.html -> POST /user/login -> select_workspace.html). */
    private long medirLogin() {
        driver.get(baseUrl() + "/login.html");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        driver.findElement(By.id("input_email")).sendKeys(EMAIL);
        driver.findElement(By.id("input_password")).sendKeys(PASSWORD);

        long start = System.nanoTime();
        driver.findElement(By.id("start_sesion_button")).click();
        // El usuario "termina" el paso cuando ve la grilla de espacios de trabajo cargada
        wait.until(ExpectedConditions.urlContains("select_workspace.html"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("board-card")));
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        return elapsed;
    }

    /** Paso 2: consultar el espacio de trabajo (click en el board -> workspace_detail.html). */
    private long medirConsultaWorkspace() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        WebElement board = driver.findElements(By.className("board-card")).stream()
                .filter(b -> b.getText().contains(WORKSPACE_NAME))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontro el workspace '" + WORKSPACE_NAME + "'. Verifique Users.json de prueba."));

        long start = System.nanoTime();
        board.click();
        wait.until(ExpectedConditions.urlContains("workspace_detail.html"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("list")));
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        return elapsed;
    }

    /** Paso 3: crear una tarjeta en el primer bloque del workspace. */
    private long medirCreacionTarjeta() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        WebElement addCardGhost = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.className("add-card-ghost")));
        addCardGhost.click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cardModal")));
        String tituloTarjeta = "Tarjeta CP-A-01 " + System.currentTimeMillis();
        driver.findElement(By.id("cardTitleInput")).sendKeys(tituloTarjeta);
        driver.findElement(By.id("cardDescInput")).sendKeys("Tarjeta creada por la prueba de aceptacion CP-A-01");
        // Se fija el valor del <input type="date"> vía JavaScript en formato
        // ISO (yyyy-MM-dd) para evitar depender del locale/formato regional
        // que Selenium usaría si se enviaran teclas directamente.
        String fechaLimite = LocalDate.now().plusDays(7).toString();
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = arguments[1];", driver.findElement(By.id("cardDueDateInput")), fechaLimite);

        long start = System.nanoTime();
        driver.findElement(By.cssSelector("#createCardForm button[type='submit']")).click();
        // El usuario "termina" el paso cuando el modal se cierra y la tarjeta aparece en el bloque
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("cardModal")));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.className("board"), "CP-A-01"));
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        return elapsed;
    }
}
