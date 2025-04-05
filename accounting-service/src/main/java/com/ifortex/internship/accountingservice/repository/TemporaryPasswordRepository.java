package com.ifortex.internship.accountingservice.repository;

import com.ifortex.internship.accountingservice.model.TemporaryPassword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemporaryPasswordRepository extends JpaRepository<TemporaryPassword, Long> {
}
