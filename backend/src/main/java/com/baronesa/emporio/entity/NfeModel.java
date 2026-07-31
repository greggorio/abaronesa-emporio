package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.TipoDocumentoFiscal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "nfe")
public class NfeModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_venda")
    private Long idVenda;

    @Column(name = "id_cliente")
    private Long idCliente;

    @Column(name = "numero")
    private String numero;

    @Column(name = "protocolo")
    private String protocolo;

    @Column(name = "motivo_rejeicao")
    private String motivoRejeicao;

    @Column(name = "serie")
    private String serie;

    @Column(name = "chave_acesso")
    private String chaveAcesso;

    @Column(name = "status")
    private String status;

    @Column(name = "xml_assinado", columnDefinition = "TEXT")
    private String xmlAssinado;

    @Column(name = "xml_retorno", columnDefinition = "TEXT")
    private String xmlRetorno;

    @Column(name = "data_emissao")
    private LocalDateTime dataEmissao;

    @Column(name = "valor_total")
    private Double valorTotal;

    @Column(name = "ambiente")
    private Integer ambiente;

    @Column(name = "modelo")
    @Builder.Default
    private Integer modelo = 55;

    public boolean isNFCe() {
        return modelo != null && modelo == 65;
    }

    public boolean isNFe() {
        return modelo == null || modelo == 55;
    }

    public TipoDocumentoFiscal getTipoDocumento() {
        return TipoDocumentoFiscal.fromModelo(modelo != null ? modelo : 55);
    }
}
