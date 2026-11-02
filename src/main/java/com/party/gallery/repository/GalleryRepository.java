package com.party.gallery.repository;
import com.party.gallery.entity.GalleryEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface GalleryRepository extends JpaRepository<GalleryEntry,Long>, JpaSpecificationExecutor<GalleryEntry> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select g from GalleryEntry g where g.id=:id and g.deleted=false")
    Optional<GalleryEntry> lock(@Param("id") Long id);
}
