package com.party.security.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginResponse {

    private Long id;
    private String username;
    private List<String> roles;
}
