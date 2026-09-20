package com.youngstersclub.app.dto;

import java.util.List;

public record WhatsAppRecipientHealthPageDto(
        List<WhatsAppRecipientHealthCustomerDto> customers,
        String nextCursor,
        boolean hasMore) {
}
