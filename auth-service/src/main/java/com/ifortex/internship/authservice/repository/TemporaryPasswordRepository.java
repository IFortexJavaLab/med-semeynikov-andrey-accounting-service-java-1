package com.ifortex.internship.authservice.repository;

import com.ifortex.internship.authservice.model.TemporaryPassword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemporaryPasswordRepository extends JpaRepository<TemporaryPassword, Long> {
}
