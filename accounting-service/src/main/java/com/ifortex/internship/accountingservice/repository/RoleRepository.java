package com.ifortex.internship.accountingservice.repository;

import com.ifortex.internship.accountingservice.model.Role;
import com.ifortex.internship.medstarter.security.model.constant.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(UserRole name);
}
