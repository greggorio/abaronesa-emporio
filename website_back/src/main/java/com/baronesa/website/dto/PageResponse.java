package com.baronesa.website.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO genérico para respostas paginadas
 * @param <T> Tipo dos dados da página
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;          // Lista de items da página atual
    private int pageNumber;            // Número da página atual (0-indexed)
    private int pageSize;              // Tamanho da página
    private long totalElements;        // Total de elementos em todas as páginas
    private int totalPages;            // Total de páginas
    private boolean first;             // Se é a primeira página
    private boolean last;              // Se é a última página
    private boolean empty;             // Se a página está vazia

    /**
     * Construtor facilitado para criar PageResponse a partir de Spring Data Page
     */
    public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> page) {
        PageResponse<T> response = new PageResponse<>();
        response.setContent(page.getContent());
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setFirst(page.isFirst());
        response.setLast(page.isLast());
        response.setEmpty(page.isEmpty());
        return response;
    }
}
