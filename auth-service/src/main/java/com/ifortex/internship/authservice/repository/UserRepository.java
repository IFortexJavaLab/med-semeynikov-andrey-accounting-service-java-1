package com.ifortex.internship.authservice.repository;

import com.ifortex.internship.authservice.model.Role;
import com.ifortex.internship.authservice.model.User;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    String BLOCKED = "BLOCKED";
    String ACTIVE = "ACTIVE";

    Optional<User> findByEmail(String email);

    Optional<User> findByUserId(String userId);

    Optional<User> findByStripeCustomerId(String stripeCustomerId);

    default Page<User> findByFilters(
        String firstName, String lastName, String phone, String email, List<String> roles, String status, Pageable pageable) {

        Specification<User> spec = Specification.where(null);

        if (firstName != null && !firstName.isEmpty()) {
            spec = spec.and((root, query, cb) ->
                cb.like(cb.lower(root.get("firstName")), "%" + firstName.toLowerCase() + "%"));
        }

        if (lastName != null && !lastName.isEmpty()) {
            spec = spec.and((root, query, cb) ->
                cb.like(cb.lower(root.get("lastName")), "%" + lastName.toLowerCase() + "%"));
        }

        if (phone != null && !phone.isEmpty()) {
            spec = spec.and((root, query, cb) ->
                cb.like(root.get("phoneNumber"), "%" + phone + "%"));
        }

        if (email != null && !email.isEmpty()) {
            spec = spec.and((root, query, cb) ->
                cb.like(cb.lower(root.get("email")), "%" + email.toLowerCase() + "%"));
        }

        if (roles != null && !roles.isEmpty()) {
            spec = spec.and((root, query, cb) -> {
                Join<User, Role> rolesJoin = root.join("roles");
                return rolesJoin.get("name").in(roles);
            });
        }

        if (status != null) {
            spec = spec.and((root, query, cb) -> {
                Expression<LocalDateTime> blockedUntil = root.get("blockedUntil");
                Predicate isBlocked = cb.greaterThan(blockedUntil, LocalDateTime.now());
                Predicate isActive = cb.or(cb.isNull(blockedUntil), cb.lessThanOrEqualTo(blockedUntil, LocalDateTime.now()));

                if (BLOCKED.equalsIgnoreCase(status)) {
                    return isBlocked;
                } else if (ACTIVE.equalsIgnoreCase(status)) {
                    return isActive;
                }
                return null;
            });
        }

        return findAll(spec, pageable);
    }

}
