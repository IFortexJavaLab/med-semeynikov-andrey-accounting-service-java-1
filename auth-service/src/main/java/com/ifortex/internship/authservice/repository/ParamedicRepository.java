package com.ifortex.internship.authservice.repository;

import com.ifortex.internship.authservice.model.Paramedic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParamedicRepository extends JpaRepository<Paramedic, Long> {
}
