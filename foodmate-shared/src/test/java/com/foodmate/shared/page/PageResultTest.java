package com.foodmate.shared.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {
    @Test
    void computesOffsetAndResult() {
        PageRequest request = new PageRequest(2, 20);
        PageResult<String> result = PageResult.of(List.of("a"), request, 41);

        assertEquals(20, request.offset());
        assertEquals(2, result.page());
        assertEquals(20, result.pageSize());
        assertEquals(41, result.total());
    }

    @Test
    void rejectsInvalidPageSize() {
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(1, 0));
    }
}

