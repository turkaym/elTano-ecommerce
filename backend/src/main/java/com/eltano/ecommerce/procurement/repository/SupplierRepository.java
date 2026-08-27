package com.eltano.ecommerce.procurement.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.eltano.ecommerce.procurement.domain.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> { }
