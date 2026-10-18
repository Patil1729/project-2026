package com.party.registration.repository;

import com.party.registration.entity.ApplicationDocument;
import com.party.registration.entity.DocumentType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {
    @EntityGraph(attributePaths = {"uploadedBy", "reviewedBy"})
    List<ApplicationDocument> findAllByApplication_IdOrderByIdDesc(Long applicationId);

    @EntityGraph(attributePaths = {"uploadedBy", "reviewedBy"})
    Optional<ApplicationDocument> findByIdAndApplication_Id(Long documentId, Long applicationId);

    // Upload holds a write lock on the parent application before reading this maximum.
    @Query("""
            select coalesce(max(d.documentVersion), 0)
            from ApplicationDocument d
            where d.application.id = :applicationId and d.documentType = :documentType
            """)
    int findMaxDocumentVersion(@Param("applicationId") Long applicationId,
                               @Param("documentType") DocumentType documentType);
}
