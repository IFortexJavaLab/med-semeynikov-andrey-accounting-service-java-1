package com.ifortex.internship.authservice.repository;

import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authservice.model.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRoleRepository extends JpaRepository<AccountRole, Long> {
    Optional<AccountRole> findByAccount(Account account);
}
