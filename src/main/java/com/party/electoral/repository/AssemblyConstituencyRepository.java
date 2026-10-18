package com.party.electoral.repository;

import com.party.electoral.entity.AssemblyConstituency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssemblyConstituencyRepository
        extends JpaRepository<AssemblyConstituency, Long> {

    boolean existsByStateCodeAndConstituencyNumber(
            String stateCode, Integer constituencyNumber);

    List<AssemblyConstituency> findByActiveTrueOrderByStateCodeAscConstituencyNumberAsc();

    List<AssemblyConstituency> findByStateCodeAndActiveTrueOrderByConstituencyNumberAsc(
            String stateCode);
}
