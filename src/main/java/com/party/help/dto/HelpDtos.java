package com.party.help.dto;
import com.party.help.entity.*;
import com.party.committee.entity.CommitteePositionType;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class HelpDtos {
    private HelpDtos() { }
    public record Create(@NotNull UUID submissionKey, @NotNull @Positive Long organizationUnitId,
        @NotBlank @Size(max=150) String contactName,
        @NotBlank @Pattern(regexp="^\\+?[1-9][0-9]{7,14}$", message="Use a normalized mobile number, for example +919876543210") String contactMobile,
        @NotBlank @Size(max=200) String locality, @NotBlank @Size(max=150) String subject,
        @NotBlank @Size(min=10,max=4000) String description, @NotNull HelpCategory category,
        boolean urgent, @AssertTrue(message="Consent is required") boolean consent) { }
    public record Action(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max=20) String action, @NotBlank @Size(min=5,max=2000) String note) { }
    public record Transfer(@NotNull @PositiveOrZero Long version, @NotBlank @Size(min=5,max=2000) String note, @Positive Long toAssignmentId) { }
    public record Comment(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max=2000) String note) { }
    public record Candidate(Long assignmentId, Long userId, String advocateName, Long organizationUnitId, String areaName, CommitteePositionType position, long openCases) { }
    public record Summary(Long id, String reference, String subject, HelpCategory category, HelpStatus status, boolean urgent,
        String areaName, String routingAreaName, String advocateName, boolean needsReassignment, Instant lastActivityAt) { }
    public record Actions(boolean canCoordinate, boolean canStart, boolean canResolve, boolean canTransfer, boolean canClose, boolean canReopen, boolean canComment, boolean canUpload) { }
    public record Attachment(Long id,String filename,String contentType,long sizeBytes) { }
    public record Detail(Summary summary, Long version, Long requesterId, String contactName, String contactMobile, String locality,
        String description, String resolutionNote, Instant resolvedAt, Actions actions, List<Attachment> attachments) { }
    public record Event(Long id,String action,String note,String actorName,String fromAdvocate,String toAdvocate,LocalDateTime createdAt) { }
}
