package com.party.help.repository;
import com.party.help.entity.HelpEvent;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface HelpEventRepository extends JpaRepository<HelpEvent,Long> {
    Page<HelpEvent> findByHelpRequest_Id(Long id,Pageable pageable);
}
