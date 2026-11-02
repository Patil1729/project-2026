package com.party.help.repository;
import com.party.help.entity.HelpAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface HelpAttachmentRepository extends JpaRepository<HelpAttachment,Long> {
    List<HelpAttachment> findByHelpRequest_IdOrderByIdAsc(Long id);
    Optional<HelpAttachment> findByIdAndHelpRequest_Id(Long id,Long requestId);
    long countByHelpRequest_Id(Long id);
}
