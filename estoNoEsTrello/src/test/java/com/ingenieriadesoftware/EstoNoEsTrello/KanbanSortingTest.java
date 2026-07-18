package com.ingenieriadesoftware.EstoNoEsTrello;

import com.ingenieriadesoftware.EstoNoEsTrello.model.Card;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class KanbanSortingTest {

    @Test
    @DisplayName("CP-UNIT-PERF-001: Pruebas algorítmicas de ordenamiento del Kanban")
    public void testKanbanCardSortingPerformance() {
        // Arrange (Preparar)
        List<Card> cards = new ArrayList<>();
        // Generar 10,000 tarjetas en orden inverso para forzar el peor caso del algoritmo
        for (int i = 10000; i > 0; i--) {
            Card c = new Card(Long.valueOf(i), "Card " + i, "Desc", LocalDate.now(), LocalDate.now().plusDays(i));
            cards.add(c);
        }

        // Act (Actuar)
        long startTime = System.nanoTime();
        // Aislamos puramente el algoritmo de ordenamiento de tarjetas por ID (prioridad)
        cards.sort(Comparator.comparing(Card::getId));
        long endTime = System.nanoTime();
        
        long durationMs = (endTime - startTime) / 1000000;

        // Assert (Afirmar)
        assertTrue(durationMs < 500, "El ordenamiento de 10000 tarjetas debe tomar menos de 500ms");
        for (int i = 0; i < cards.size() - 1; i++) {
            assertTrue(cards.get(i).getId() < cards.get(i+1).getId(), "Las tarjetas deben estar ordenadas matemáticamente de menor a mayor");
        }
    }
}
