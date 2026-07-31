package com.baronesa.emporio.dto.mercadopago;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MercadoPagoCustomer {

    @NotBlank(message = "Nome do cliente é obrigatório")
    private String name;

    @NotBlank(message = "Email do cliente é obrigatório")
    @Email(message = "Email deve ser válido")
    private String email;

    private String cpf;
    private String phone;
}
