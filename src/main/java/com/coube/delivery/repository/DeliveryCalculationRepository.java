package com.coube.delivery.repository;

import com.coube.delivery.entity.DeliveryCalculationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryCalculationRepository extends JpaRepository<DeliveryCalculationEntity, Long> {
}
