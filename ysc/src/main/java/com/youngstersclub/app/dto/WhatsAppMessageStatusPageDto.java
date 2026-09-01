package com.youngstersclub.app.dto;

import java.util.List;

public class WhatsAppMessageStatusPageDto {

    private final List<WhatsAppTrackedMessageDto> messages;
    private final int page;
    private final int pageSize;
    private final boolean hasMore;

    public WhatsAppMessageStatusPageDto(
            List<WhatsAppTrackedMessageDto> messages,
            int page,
            int pageSize,
            boolean hasMore) {
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.page = page;
        this.pageSize = pageSize;
        this.hasMore = hasMore;
    }

    public List<WhatsAppTrackedMessageDto> getMessages() {
        return messages;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public boolean isHasMore() {
        return hasMore;
    }
}
