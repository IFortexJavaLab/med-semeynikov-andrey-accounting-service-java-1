package com.ifortex.internship.authservice.model;

import com.ifortex.internship.authservice.model.constant.UserStatus;
import com.ifortex.internship.authservice.stripe.model.StripeSubscription;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String userId;

  @Column(nullable = false)
  private String email;

  @Column() private String password;

  @OneToOne(
      mappedBy = "user",
      cascade = {CascadeType.MERGE, CascadeType.PERSIST},
      orphanRemoval = true)
  private TemporaryPassword temporaryPassword;

  @Column(nullable = false)
  private boolean isTwoFactorEnabled = true;

  @ManyToMany(
      fetch = FetchType.EAGER,
      cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @JoinTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  private List<Role> roles = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Column(nullable = false)
  private boolean isSoftDeleted = false;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserStatus status = UserStatus.ACTIVE;

  @OneToOne(mappedBy = "user", cascade = CascadeType.REMOVE, orphanRemoval = true)
  private RefreshToken refreshToken;

  private String stripeCustomerId;

  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private List<StripeSubscription> stripeSubscriptions = new ArrayList<>();
}
