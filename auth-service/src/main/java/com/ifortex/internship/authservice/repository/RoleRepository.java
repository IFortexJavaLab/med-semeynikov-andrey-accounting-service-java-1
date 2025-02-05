package com.ifortex.internship.authservice.repository;

import com.ifortex.internship.authservice.model.constant.UserRole;
import com.ifortex.internship.authservice.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(UserRole name);
}