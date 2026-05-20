package com.example.stockchart.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerAccount {
    private String name;
    private String passwordHash;
    @Builder.Default
    private boolean mustChangePassword = false;
}
