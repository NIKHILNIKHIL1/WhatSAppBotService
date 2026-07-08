package com.bot.whatsappbotservice.customer;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<Customer> findByPhoneNumber(String phoneNumber);

    Page<Customer> findByStatus(CustomerStatus status, Pageable pageable);
}
