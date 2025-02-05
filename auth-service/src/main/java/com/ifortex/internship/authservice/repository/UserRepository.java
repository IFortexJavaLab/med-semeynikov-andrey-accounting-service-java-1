package com.ifortex.internship.authservice.repository;

import com.ifortex.internship.authservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  Optional<User> findByUserId(String userId);

  List<User> findByUserIdIn(List<String> userIds);
}
