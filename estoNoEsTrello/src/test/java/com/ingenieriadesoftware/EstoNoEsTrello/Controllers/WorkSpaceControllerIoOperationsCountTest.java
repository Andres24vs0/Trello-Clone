package com.ingenieriadesoftware.EstoNoEsTrello.Controllers;

import com.ingenieriadesoftware.EstoNoEsTrello.JsonControllers.UserJsonController;
import com.ingenieriadesoftware.EstoNoEsTrello.model.User;
import com.ingenieriadesoftware.EstoNoEsTrello.model.WorkSpace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;

/**
 * CP-U-03 — WorkSpaceController.addWorkSpace(WorkSpace, User)
 * Plan de Pruebas ST-KANBAN-PERF-PLAN, sección 5.1 / 6.1.
 * <p>
 * Entrada: usuario con 1 espacio de trabajo existente; se espía (Mockito)
 * UserJsonController.
 * Resultado esperado: se documenta el número de operaciones de
 * lectura/escritura de disco por transacción. Meta deseable: 1 lectura +
 * 1 escritura (actualmente son 3 operaciones: findTotalUsers + deleteUser
 * + saveUser).
 * <p>
 * TÉCNICA: Mockito 5 (incluido transitivamente en spring-boot-starter-test,
 * sin dependencias adicionales) permite mockear métodos ESTÁTICOS con
 * Mockito.mockStatic(...), tal como indica el plan en su columna de
 * herramientas ("no requiere dependencia adicional").
 */
class WorkSpaceControllerIoOperationsCountTest {

    @Test
    @DisplayName("CP-U-03: addWorkSpace() invoca 3 operaciones de E/S "
            + "(findTotalUsers + deleteUser + saveUser); meta deseable = 2")
    void addWorkSpace_shouldDocumentNumberOfIoOperationsPerTransaction() throws IOException {
        // Arrange: usuario de prueba con 1 espacio de trabajo existente
        User testUser = new User("io-count@test.com", "Password123!");
        ArrayList<WorkSpace> existingWorkspaces = new ArrayList<>();
        existingWorkspaces.add(new WorkSpace(1L, "Espacio existente", "Descripcion", new ArrayList<>()));
        testUser.setWorkspaces(existingWorkspaces);

        // Usuario tal como lo "encontraría" findTotalUsers() en Users.json
        User persistedUser = new User("io-count@test.com", "Password123!");
        persistedUser.setWorkspaces(new ArrayList<>(existingWorkspaces));
        List<User> allPersistedUsers = new ArrayList<>(List.of(persistedUser));

        try (MockedStatic<UserJsonController> mockedStatic = Mockito.mockStatic(UserJsonController.class)) {
            mockedStatic.when(UserJsonController::findTotalUsers)
                    .thenReturn(new ArrayList<>(allPersistedUsers));
            mockedStatic.when(() -> UserJsonController.deleteUser(anyString()))
                    .thenAnswer(invocation -> null);
            mockedStatic.when(() -> UserJsonController.saveUser(any(User.class)))
                    .thenAnswer(invocation -> null);

            // Act
            WorkSpace nuevoEspacio = new WorkSpace(2L, "Espacio nuevo", "Descripcion nueva", new ArrayList<>());
            WorkSpaceController.addWorkSpace(nuevoEspacio, testUser);

            // Assert: se cuentan las invocaciones reales a la capa de persistencia
            mockedStatic.verify(UserJsonController::findTotalUsers, times(1));
            mockedStatic.verify(() -> UserJsonController.deleteUser(anyString()), times(1));
            mockedStatic.verify(() -> UserJsonController.saveUser(any(User.class)), times(1));

            int totalOperacionesDetectadas = 1 /* findTotalUsers */
                    + 1 /* deleteUser  */
                    + 1 /* saveUser    */;

            System.out.println("=== CP-U-03: operaciones de E/S detectadas en addWorkSpace() ===");
            System.out.println("findTotalUsers(): 1 lectura completa de Users.json");
            System.out.println("deleteUser():      1 lectura + 1 escritura completa de Users.json");
            System.out.println("saveUser():        1 lectura + 1 escritura completa de Users.json");
            System.out.println("Total de llamadas a la capa de persistencia: " + totalOperacionesDetectadas
                    + " (meta deseable declarada en el plan: 1 lectura + 1 escritura)");

            // Se documenta el número real de llamadas actuales (defecto de eficiencia
            // conocido, no bloqueante): addWorkSpace() hace 3 llamadas a
            // UserJsonController en lugar de las 2 (1 lectura + 1 escritura) que
            // serían la meta deseable de eficiencia de recursos.
            assertEquals(3, totalOperacionesDetectadas,
                    "El número de operaciones de E/S de addWorkSpace() cambió respecto al "
                            + "documentado en el plan de pruebas; actualice el hallazgo de eficiencia.");
        }
    }
}
