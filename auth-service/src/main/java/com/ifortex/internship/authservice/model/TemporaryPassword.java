package com.ifortex.internship.authservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class TemporaryPassword {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  private String temporaryPasswordHash;

  private LocalDateTime expirationDate;

  public TemporaryPassword(User user, String temporaryPasswordHash, LocalDateTime expirationDate) {
    this.user = user;
    this.temporaryPasswordHash = temporaryPasswordHash;
    this.expirationDate = expirationDate;
  }
}
