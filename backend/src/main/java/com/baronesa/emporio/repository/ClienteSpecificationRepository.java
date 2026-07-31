package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ClienteSpecificationRepository {

    private final EntityManager em;

    public Page<Usuario> findAllClientes(Specification<Usuario> spec, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Query para dados
        CriteriaQuery<Usuario> query = cb.createQuery(Usuario.class);
        Root<Usuario> root = query.from(Usuario.class);

        // Filtro base: apenas CLIENTE
        Join<Usuario, Usuario.Role> rolesJoin = root.join("roles", JoinType.INNER);
        Predicate clientePredicate = cb.equal(rolesJoin, Usuario.Role.CLIENTE);

        // Combinar com especificação adicional se houver
        Predicate finalPredicate = clientePredicate;
        if (spec != null) {
            Predicate specPredicate = spec.toPredicate(root, query, cb);
            if (specPredicate != null) {
                finalPredicate = cb.and(clientePredicate, specPredicate);
            }
        }

        query.where(finalPredicate);
        query.distinct(true);

        // Aplicar ordenação
        if (pageable.getSort().isSorted()) {
            query.orderBy(pageable.getSort().stream()
                    .map(order -> order.isAscending()
                            ? cb.asc(root.get(order.getProperty()))
                            : cb.desc(root.get(order.getProperty())))
                    .toList());
        }

        TypedQuery<Usuario> typedQuery = em.createQuery(query);

        if (pageable.isPaged()) {
            typedQuery.setFirstResult((int) pageable.getOffset());
            typedQuery.setMaxResults(pageable.getPageSize());
        }

        List<Usuario> resultList = typedQuery.getResultList();

        // Query para count
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Usuario> countRoot = countQuery.from(Usuario.class);
        Join<Usuario, Usuario.Role> countRolesJoin = countRoot.join("roles", JoinType.INNER);

        Predicate countClientePredicate = cb.equal(countRolesJoin, Usuario.Role.CLIENTE);
        Predicate countFinalPredicate = countClientePredicate;

        if (spec != null) {
            Predicate countSpecPredicate = spec.toPredicate(countRoot, countQuery, cb);
            if (countSpecPredicate != null) {
                countFinalPredicate = cb.and(countClientePredicate, countSpecPredicate);
            }
        }

        countQuery.select(cb.countDistinct(countRoot));
        countQuery.where(countFinalPredicate);

        Long total = em.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(resultList, pageable, total);
    }
}