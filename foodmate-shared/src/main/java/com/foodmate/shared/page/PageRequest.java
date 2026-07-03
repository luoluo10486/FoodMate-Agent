package com.foodmate.shared.page;

public record PageRequest(int page, int pageSize) {
    public PageRequest {
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("pageSize must be between 1 and 200");
        }
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}

