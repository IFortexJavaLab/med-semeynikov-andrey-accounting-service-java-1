package com.ifortex.internship.accountingservice.repository;

import com.ifortex.internship.accountingservice.model.Paramedic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParamedicRepository extends JpaRepository<Paramedic, Long> {
}
