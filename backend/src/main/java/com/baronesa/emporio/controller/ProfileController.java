package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.auth.AuthApiResponse;
import com.baronesa.emporio.dto.profile.ChangePasswordRequest;
import com.baronesa.emporio.dto.profile.ProfileResponse;
import com.baronesa.emporio.dto.profile.UpdateProfileRequest;
import com.baronesa.emporio.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Profile", description = "Gerenciamento de perfil do usuário")
public class ProfileController {

    private final UserProfileService profileService;

    @GetMapping
    @Operation(summary = "Obter perfil", description = "Retorna perfil completo do usuário autenticado")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Perfil retornado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<ProfileResponse> getProfile() {
        ProfileResponse response = profileService.getCurrentProfile();
        return ResponseEntity.ok(response);
    }

    @PutMapping
    @Operation(summary = "Atualizar perfil", description = "Atualiza dados do perfil do usuário")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Perfil atualizado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<ProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        ProfileResponse response = profileService.updateProfile(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/password")
    @Operation(summary = "Alterar senha", description = "Altera a senha do usuário autenticado")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Senha alterada com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Senha atual incorreta ou dados inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<AuthApiResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(request);
        return ResponseEntity.ok(AuthApiResponse.success("Senha alterada com sucesso"));
    }
}
