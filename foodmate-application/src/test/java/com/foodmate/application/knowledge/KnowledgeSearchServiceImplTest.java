package com.foodmate.application.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.port.out.KnowledgeSearchPort;
import com.foodmate.application.knowledge.service.impl.KnowledgeSearchServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class KnowledgeSearchServiceImplTest {
    private final KnowledgeSearchPort search = mock(KnowledgeSearchPort.class);
    private final KnowledgeRepository store = mock(KnowledgeRepository.class);
    private final KnowledgeSearchServiceImpl service =
            new KnowledgeSearchServiceImpl(provider(search), provider(store));

    @Test
    void filtersRuntimeCitationsAgainstCurrentPublishedPostgresState() {
        var visible =
                new KnowledgeSearchPort.Citation(7, "chunk-7", "Guide", "v2", "Protein", "safe");
        var hidden =
                new KnowledgeSearchPort.Citation(8, "chunk-8", "Hidden", "v1", "", "not returned");
        when(search.search("protein", "public_published"))
                .thenReturn(new KnowledgeSearchPort.SearchResult(List.of(visible, hidden)));
        when(store.isPublicPublished(7, "v2")).thenReturn(true);
        when(store.isPublicPublished(8, "v1")).thenReturn(false);

        var result = service.search(" protein ");

        assertEquals(
                List.of(7L),
                result.citations().stream().map(citation -> citation.documentId()).toList());
    }

    @Test
    void rejectsBlankQueryBeforeCallingRuntime() {
        assertThrows(IllegalArgumentException.class, () -> service.search("  "));
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            public T getObject(Object... args) {
                return value;
            }

            public T getObject() {
                return value;
            }

            public T getIfAvailable() {
                return value;
            }

            public T getIfUnique() {
                return value;
            }
        };
    }
}
