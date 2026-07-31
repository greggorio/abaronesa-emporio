package com.baronesa.emporio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class MercadoPagoPixRequest {

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal amount;

    @Size(max = 200, message = "Descrição não pode ter mais de 200 caracteres")
    private String description;

    @JsonProperty("external_reference")
    @Size(max = 100, message = "Referência externa não pode ter mais de 100 caracteres")
    private String externalReference;

    @Valid
    @NotNull(message = "Dados do cliente são obrigatórios")
    private CustomerData customer;

    // Construtor padrão
    public MercadoPagoPixRequest() {
    }

    // Getters e Setters
    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public CustomerData getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerData customer) {
        this.customer = customer;
    }

    @Override
    public String toString() {
        return "MercadoPagoPixRequest{" +
                "amount=" + amount +
                ", description='" + description + '\'' +
                ", externalReference='" + externalReference + '\'' +
                ", customer=" + customer +
                '}';
    }

    // Classe interna para dados do cliente
    public static class CustomerData {

        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 100, message = "Nome não pode ter mais de 100 caracteres")
        private String name;

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email deve ter formato válido")
        private String email;

        @Pattern(regexp = "\\d{11}", message = "CPF deve ter 11 dígitos")
        private String cpf;

        @Pattern(regexp = "\\d{10,11}", message = "Telefone deve ter 10 ou 11 dígitos")
        private String phone;

        // Construtor padrão
        public CustomerData() {
        }

        // Getters e Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getCpf() {
            return cpf;
        }

        public void setCpf(String cpf) {
            this.cpf = cpf;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        @Override
        public String toString() {
            return "CustomerData{" +
                    "name='" + name + '\'' +
                    ", email='" + email + '\'' +
                    ", cpf='" + cpf + '\'' +
                    ", phone='" + phone + '\'' +
                    '}';
        }
    }
}